// Package miniapp menyediakan REST API untuk Telegram Mini App via Fiber.
// Routing tunggal di Routes(); auth dilakukan via middleware berdasarkan
// header X-Mini-Token kecuali untuk endpoint /api/mini/auth.
package miniapp

import (
	"github.com/gofiber/fiber/v2"

	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/kolektas"
	"github.com/hikari-work/cek-pelunasan/internal/service/paymentdetails"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// Deps mengumpulkan service yang dibutuhkan miniapp.
type Deps struct {
	BotToken        string
	SessionTTL      int // menit, default 60 kalau 0
	Auth            *auth.AuthorizedChats
	Users           *users.Service
	Bills           *bill.Service
	Savings         *savings.Service
	KolekTas        *kolektas.Service
	PaymentDetails  *paymentdetails.Service
}

// Register memasang seluruh route /api/mini/* ke app Fiber.
func Register(app *fiber.App, d Deps) {
	ttl := d.SessionTTL
	if ttl <= 0 {
		ttl = 60
	}
	verifier := newInitDataVerifier(d.BotToken)
	store := newSessionStore(ttl)

	mini := app.Group("/api/mini")
	mini.Post("/auth", authHandler(verifier, store, d.Auth, d.Users))

	// Semua subgroup di bawah ini butuh session token.
	authed := mini.Use(sessionMiddleware(store))

	registerTagihan(authed, d.Bills)
	registerTabungan(authed, d.Savings)
	registerCanvas(authed, d.Savings)
	registerKolekTas(authed, d.KolekTas)
	registerPayment(authed, d.Bills, d.PaymentDetails)
	registerPelunasan(authed, d.Bills)
}
