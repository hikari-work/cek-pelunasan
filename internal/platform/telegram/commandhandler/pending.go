package commandhandler

import (
	"context"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
)

// pendingHandler memberi tahu pengguna kalau fitur sedang dimigrasikan.
// Tujuannya supaya bot tidak diam saat command di-trigger — minimal
// pengguna tahu fitur belum diaktifkan di rilis Go.
type pendingHandler struct {
	cmd  string
	desc string
}

func (h *pendingHandler) Command() string     { return h.cmd }
func (h *pendingHandler) Description() string { return h.desc }
func (h *pendingHandler) Handle(_ context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	_, _ = b.SendText(msg.Chat.ID, "🚧 Perintah `"+h.cmd+"` masih dimigrasikan ke versi Go. Coba lagi nanti.")
}
