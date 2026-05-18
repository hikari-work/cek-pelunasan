// Package callbackhandler berisi handler untuk inline button callback.
// Stub default untuk callback yang belum diport — supaya tombol pagination,
// branch picker, dll tetap responsif (tidak loading spinner forever).
package callbackhandler

import (
	"context"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
)

// None handler default untuk callback dengan prefix yang tidak dikenal.
// Kirim notifikasi singkat ke user sebagai feedback.
type None struct{}

func (h *None) Prefix() string { return "none" }
func (h *None) Handle(_ context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	_ = b.AnswerCallback(q.ID, "Tombol ini tidak aktif.")
}

// pendingHandler stub callback yang belum diport.
type pendingHandler struct {
	prefix string
}

func (h *pendingHandler) Prefix() string { return h.prefix }
func (h *pendingHandler) Handle(_ context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	_ = b.AnswerCallback(q.ID, "Fitur sedang dimigrasikan.")
}

// Pending stub callback untuk prefix tertentu.
func Pending(prefix string) telegram.CallbackHandler {
	return &pendingHandler{prefix: prefix}
}
