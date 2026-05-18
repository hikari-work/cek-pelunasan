package whatsapp

import (
	"context"
	"log/slog"
	"regexp"
	"strings"
	"sync"

	"github.com/gofiber/fiber/v2"
)

// Handler menangani satu kategori command WhatsApp.
type Handler interface {
	// Match true kalau handler ini mau memproses pesan tersebut.
	// Router pertama yang return true akan dipakai (dispatch eksklusif).
	Match(w *Webhook) bool
	// Handle proses pesan. Eksekusi async (router sudah goroutine).
	Handle(ctx context.Context, w *Webhook)
}

// Router daftar handler urut prioritas. Yang lebih spesifik harus didaftar dulu.
type Router struct {
	mu       sync.RWMutex
	handlers []Handler
	admin    string // nomor admin tanpa @s.whatsapp.net
}

func NewRouter(adminPhone string) *Router {
	return &Router{admin: adminPhone}
}

func (r *Router) Add(h Handler) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.handlers = append(r.handlers, h)
}

// AdminPhone untuk handler yang perlu cek admin.
func (r *Router) AdminPhone() string { return r.admin }

func (r *Router) IsFromAdmin(w *Webhook) bool {
	if r.admin == "" || w == nil {
		return false
	}
	return strings.Contains(w.From, r.admin)
}

func (r *Router) Dispatch(ctx context.Context, w *Webhook) {
	if w == nil || w.Event != "message" || w.Payload == nil || w.Payload.Body == "" {
		return
	}
	r.mu.RLock()
	handlers := r.handlers
	r.mu.RUnlock()
	for _, h := range handlers {
		if h.Match(w) {
			go func(h Handler) {
				defer func() {
					if rec := recover(); rec != nil {
						slog.Error("panic in whatsapp handler", "panic", rec)
					}
				}()
				h.Handle(ctx, w)
			}(h)
			return
		}
	}
	slog.Debug("whatsapp: no handler matched", "body", w.Payload.Body)
}

// MountWebhook pasang endpoint POST /v2/whatsapp ke Fiber.
// Selalu return 200 OK dengan body "OK" — gateway punya timeout ketat.
//
// Dispatch dijalankan di goroutine dengan context.Background() yang independen
// dari request: kita sudah balas 200, tapi pemrosesan masih berjalan dan tidak
// boleh ikut dibatalkan oleh Fiber saat response selesai.
func MountWebhook(app *fiber.App, r *Router) {
	app.Post("/v2/whatsapp", func(c *fiber.Ctx) error {
		var w Webhook
		if err := c.BodyParser(&w); err != nil {
			slog.Warn("whatsapp webhook bad payload", "err", err)
			return c.Status(fiber.StatusOK).SendString("OK")
		}
		go r.Dispatch(context.Background(), &w)
		return c.Status(fiber.StatusOK).SendString("OK")
	})
}

// HotKolekPattern HotKolekPattern: titik diikuti 12 digit, optional tambahan dipisah spasi.
// Contoh: ".010600001234" atau ".010600001234 010600005678".
var HotKolekPattern = regexp.MustCompile(`^\.\d{12}(?:\s\d{12})*$`)
