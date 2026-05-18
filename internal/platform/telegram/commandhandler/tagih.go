package commandhandler

import (
	"context"
	"strconv"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Tagih /tagih <no_spk> — tampilkan detail satu tagihan.
type Tagih struct {
	Bills *bill.Service
}

func (h *Tagih) Command() string { return "/tagih" }
func (h *Tagih) Description() string {
	return "Mengembalikan rincian tagihan berdasarkan ID SPK yang anda kirimkan"
}

func (h *Tagih) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.SplitN(strings.TrimSpace(msg.Text), " ", 2)
	if len(parts) < 2 || strings.TrimSpace(parts[1]) == "" {
		_, _ = b.SendText(chatID, utils.MsgInvalidDeauthFormat)
		return
	}
	id := strings.TrimSpace(parts[1])

	start := time.Now()
	bills, err := h.Bills.GetByID(ctx, id)
	if err != nil || bills == nil {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}
	text := h.Bills.DetailMarkdown(ctx, bills)
	text += "\nEksekusi dalam " + strconv.FormatInt(time.Since(start).Milliseconds(), 10) + " ms"
	_, _ = b.SendText(chatID, text)
}
