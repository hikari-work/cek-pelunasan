package whahandler

import (
	"context"
	"log/slog"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

// Shortcut menangani pesan admin yang diawali "/" — preset balasan untuk
// pertanyaan CS yang sering berulang. Admin ketik "/coba" → bot edit
// pesan tersebut menjadi "silahkan bisa dicoba kembali kak".
//
// Hanya aktif kalau pesan berasal dari admin (DM atau di group). Pesan
// dari non-admin yang diawali "/" akan diabaikan supaya tidak ada user
// yang tidak sengaja men-trigger preset.
//
// Action: EditMessage. Pesan asli admin di-replace dengan balasan,
// memberi efek "admin sendiri yang ngetik balasan" tanpa double bubble
// di chat.
type Shortcut struct {
	Sender *whatsapp.Sender
	Router *whatsapp.Router
}

// shortcutPresets daftar balasan preset. Sengaja literal — daftar pendek
// dan tidak pernah berubah dinamis. Kalau perlu reload runtime, refactor
// jadi config-driven.
var shortcutPresets = map[string]string{
	"/coba":    "silahkan bisa dicoba kembali kak",
	"/kasih":   "terima kasih kembali kak 🙏",
	"/tunggu":  "baik, mohon ditunggu kak",
	"/relog":   "silahkan usernya relogin terlebih dahulu kak, kemudian bisa dicoba kembali",
	"/selesai": "kalo sudah selesai kami diinfo ya kak",
	"/enter":   "enter lagi kak, kalo sudah kami diinfo ya kak",
	"/input":   "baik kak sudah kami input ya 🙏",
	"/display": "untuk display tersebut sudah dibebaskan ya kak, bisa dicoba kembali 🙏",
	"/terima":  "terimakasih kak 🙏",
}

func (h *Shortcut) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil || h.Router == nil {
		return false
	}
	if !strings.HasPrefix(m.Body, "/") {
		return false
	}
	return h.Router.IsFromAdmin(m)
}

func (h *Shortcut) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil {
		return
	}

	// Trim trailing whitespace supaya "/coba " tetap match. Body sudah di-trim
	// dari adapter, tapi safety belt kalau ada whitespace di tengah-akhir.
	cmd := strings.TrimSpace(m.Body)
	reply, ok := shortcutPresets[cmd]
	if !ok {
		// Match() sudah filter prefix "/" + admin, tapi shortcut yang tidak
		// terdaftar (mis. "/foo") cukup di-log warn — jangan reply error
		// supaya tidak bocor ke user.
		slog.Warn("shortcut: unknown command", "cmd", cmd, "sender", m.Info.Sender.String())
		return
	}

	if err := h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID, reply); err != nil {
		slog.Warn("shortcut: edit pesan gagal", "cmd", cmd, "err", err)
	}
}
