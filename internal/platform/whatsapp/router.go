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
func (r *Router) IsFromAdmin(m *IncomingMessage) bool {
	if r == nil || r.admin == "" || m == nil {
		return false
	}
	return strings.Contains(m.Info.Sender.User, r.admin)
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
		return
	}
	if msg.IsFromMe {
		// Pesan dari device kita sendiri (mis. balasan auto dari bot lain
		// di akun yang sama) — skip supaya tidak loop.
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
