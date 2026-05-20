package whatsapp

import (
	"context"
	"log/slog"
	"strings"
	"sync"

	"go.mau.fi/whatsmeow/types/events"
)

// Handler menangani satu kategori pesan WhatsApp.
//
// Match dipanggil pertama untuk seluruh handler terdaftar — yang pertama
// return true akan dipakai (dispatch eksklusif). Handle lalu jalan di
// goroutine sendiri dengan recover panic.
type Handler interface {
	// Match true kalau handler ini ingin memproses pesan tersebut.
	// Implementasi harus cepat (string check) — jangan I/O di sini.
	Match(m *IncomingMessage) bool

	// Handle proses pesan. Boleh blocking — sudah jalan di goroutine.
	// Context dilahirkan dari context.Background() (independen dari event),
	// tapi tetap ikut signal shutdown root via WithCancel saat router
	// dipasang ke client.
	Handle(ctx context.Context, m *IncomingMessage)
}

// Router daftar handler urut prioritas. Yang lebih spesifik harus didaftarkan
// duluan supaya match lebih awal.
type Router struct {
	mu       sync.RWMutex
	handlers []Handler

	// admin nomor admin tanpa @s.whatsapp.net. Helper IsFromAdmin /
	// IsAdminInGroup pakai field ini.
	admin string

	// allowSelf izinkan pesan IsFromMe (dari device bot sendiri) tetap
	// di-dispatch. Default false. Berguna untuk single-account dev/testing.
	allowSelf bool

	// rootCtx dipakai sebagai parent context untuk setiap dispatch.
	// Diisi saat AttachToClient dipanggil. Selama belum diset, fallback
	// ke context.Background.
	rootCtx context.Context
}

// NewRouter bangun router. adminPhone boleh kosong kalau tidak ada
// concept "admin" (semua handler treat sender setara).
func NewRouter(adminPhone string) *Router {
	return &Router{admin: strings.TrimSpace(adminPhone)}
}

// SetAllowSelfMessages set apakah pesan dari device bot sendiri di-dispatch.
// Default false (skip untuk hindari loop). Aman dipanggil sebelum AttachToClient.
func (r *Router) SetAllowSelfMessages(allow bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.allowSelf = allow
}

// Add daftarkan handler. Dispatch order = urutan Add — yang duluan
// daftar duluan dicek matching-nya.
func (r *Router) Add(h Handler) {
	if h == nil {
		return
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.handlers = append(r.handlers, h)
}

// AdminPhone return nomor admin yang dikonfigurasi.
func (r *Router) AdminPhone() string { return r.admin }

// IsFromAdmin true kalau pesan datang dari admin (DM atau dia yang
// kirim di grup). Dipakai handler shortcut, .minbunga, .jb, dll.
//
// Tiga kondisi yang dianggap admin:
//
//  1. IsFromMe — pesan dari akun bot sendiri. Kalau bot di-pair ke
//     nomor admin (single-account setup), setiap pesan yang Anda kirim
//     dari HP itu masuk ke handler dengan IsFromMe=true. By definition,
//     ini dari pemilik akun = admin.
//  2. Sender.User contains adminPhone — admin kirim DM dari nomor lain
//     ke bot.
//  3. SenderAlt.User contains adminPhone — pesan masuk addressing mode
//     LID, dan whatsmeow sudah punya mapping phone-nya.
func (r *Router) IsFromAdmin(m *IncomingMessage) bool {
	if r == nil || m == nil {
		return false
	}
	if m.IsFromMe {
		return true
	}
	if r.admin == "" {
		return false
	}
	if strings.Contains(m.Info.Sender.User, r.admin) {
		return true
	}
	if strings.Contains(m.Info.SenderAlt.User, r.admin) {
		return true
	}
	return false
}

// IsAdminInGroup true kalau pesan dikirim oleh admin di chat grup.
// Dipakai handler yang khusus aktif untuk admin saat dia ada di grup
// (mis. .reset, .min legacy).
func (r *Router) IsAdminInGroup(m *IncomingMessage) bool {
	return r.IsFromAdmin(m) && m != nil && m.IsGroup
}

// AttachToClient pasang router sebagai event handler ke whatsmeow client.
// rootCtx dipakai sebagai parent untuk dispatch goroutine — saat ctx batal,
// tidak ada dispatch baru yang dijalankan.
//
// Aman dipanggil sekali. Kalau dipanggil lebih dari sekali, event akan
// dispatch berkali-kali (whatsmeow tidak dedupe handler).
func (r *Router) AttachToClient(c *Client, rootCtx context.Context) {
	if c == nil || c.WAClient == nil {
		return
	}
	r.mu.Lock()
	r.rootCtx = rootCtx
	r.mu.Unlock()
	c.WAClient.AddEventHandler(r.eventHandler)
}

// eventHandler entry point untuk semua event whatsmeow. Hanya pesan teks
// (events.Message) yang di-dispatch — event lain (ack, presence) diabaikan.
func (r *Router) eventHandler(rawEvt any) {
	evt, ok := rawEvt.(*events.Message)
	if !ok {
		return
	}
	msg := fromEvent(evt)
	if msg == nil {
		slog.Debug("whatsapp: fromEvent return nil — pesan tanpa body & tanpa media",
			"id", evt.Info.ID)
		return
	}
	slog.Debug("whatsapp: event masuk",
		"sender", msg.Info.Sender.String(),
		"sender_alt", msg.Info.SenderAlt.String(),
		"addressing_mode", string(msg.Info.AddressingMode),
		"chat", msg.Info.Chat.String(),
		"is_from_me", msg.IsFromMe,
		"is_group", msg.IsGroup,
		"media", msg.MediaKind,
		"body", truncate(msg.Body, 80),
	)

	r.mu.RLock()
	allowSelf := r.allowSelf
	r.mu.RUnlock()

	if msg.IsFromMe && !allowSelf {
		slog.Debug("whatsapp: skip IsFromMe (set WA_ALLOW_SELF_MESSAGES=true untuk dispatch)",
			"sender", msg.Info.Sender.String())
		return
	}
	r.dispatch(msg)
}

// dispatch jalankan handler pertama yang Match. Setiap Handle jalan di
// goroutine sendiri dengan recover panic dan context turunan dari rootCtx.
func (r *Router) dispatch(m *IncomingMessage) {
	r.mu.RLock()
	handlers := r.handlers
	rootCtx := r.rootCtx
	r.mu.RUnlock()

	if rootCtx == nil {
		rootCtx = context.Background()
	}

	for _, h := range handlers {
		if !h.Match(m) {
			continue
		}
		go func(h Handler) {
			defer func() {
				if rec := recover(); rec != nil {
					slog.Error("panic in whatsapp handler",
						"panic", rec,
						"sender", m.Info.Sender.String(),
						"body", truncate(m.Body, 80),
					)
				}
			}()
			h.Handle(rootCtx, m)
		}(h)
		return
	}
	slog.Debug("whatsapp: no handler matched",
		"sender", m.Info.Sender.String(),
		"body", truncate(m.Body, 80),
	)
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "…"
}
