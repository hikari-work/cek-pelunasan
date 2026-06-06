package miniapp

import (
	"context"
	"time"

	"github.com/gofiber/fiber/v2"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

const (
	headerToken = "X-Mini-Token"
	ctxSession  = "miniapp_session"
)

// authHandler memverifikasi initData, memastikan chatId terdaftar di Users,
// lalu mengeluarkan session token.
func authHandler(verifier *initDataVerifier, store *sessionStore, authed *auth.AuthorizedChats, usersSvc *users.Service) fiber.Handler {
	return func(c *fiber.Ctx) error {
		var req struct {
			InitData string `json:"initData"`
		}
		if err := c.BodyParser(&req); err != nil {
			return c.SendStatus(fiber.StatusBadRequest)
		}
		res := verifier.Verify(req.InitData)
		if !res.Valid {
			return c.SendStatus(fiber.StatusUnauthorized)
		}

		ctx, cancel := context.WithTimeout(c.UserContext(), 5*time.Second)
		defer cancel()
		user, err := usersSvc.FindByChatID(ctx, res.ChatID)
		if err != nil {
			return c.SendStatus(fiber.StatusInternalServerError)
		}
		if user == nil {
			return c.SendStatus(fiber.StatusForbidden)
		}
		if !authed.IsAuthorized(res.ChatID) {
			authed.AddChat(res.ChatID)
		}

		sess := store.Create(res.ChatID, user.Roles)
		return c.JSON(fiber.Map{
			"token": sess.Token,
			"user": fiber.Map{
				"chatId":    res.ChatID,
				"firstName": res.FirstName,
				"roles":     user.Roles,
			},
		})
	}
}

// sessionMiddleware menolak request kalau header X-Mini-Token tidak valid /
// expired. Session ditaruh di Locals(ctxSession) untuk handler downstream.
// Jika request dari localhost, bypass token check (dev mode).
func sessionMiddleware(store *sessionStore) fiber.Handler {
	return func(c *fiber.Ctx) error {
		// Bypass auth untuk localhost (dev mode)
		ip := c.IP()
		if ip == "127.0.0.1" || ip == "::1" {
			c.Locals(ctxSession, session{
				Token:     "dev-localhost",
				ChatID:    0,
				Roles:     entity.RoleAdmin,
				ExpiresAt: time.Now().Add(24 * time.Hour),
			})
			return c.Next()
		}

		tok := c.Get(headerToken)
		sess, ok := store.Get(tok)
		if !ok {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error":   "Unauthorized",
				"message": "Token tidak valid atau sudah kedaluwarsa",
			})
		}
		c.Locals(ctxSession, sess)
		return c.Next()
	}
}

// pingBypassHandler memproses login langsung dari user intranet yang lolos ping check.
func pingBypassHandler(store *sessionStore) fiber.Handler {
	return func(c *fiber.Ctx) error {
		sess := store.Create(101011, entity.RoleAdmin)
		return c.JSON(fiber.Map{
			"token": sess.Token,
			"user": fiber.Map{
				"chatId":    101011,
				"firstName": "Intranet User",
				"roles":     entity.RoleAdmin,
			},
		})
	}
}

