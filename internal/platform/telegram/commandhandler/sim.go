package commandhandler

import (
	"context"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/simulasiangsuran"
)

// Sim /simangsuran <no_spk> — simulasi angsuran minimal supaya kolektibilitas
// tetap di posisi terbaik. Padanan SimulasiAngsuranCommand.
type Sim struct {
	Bills *bill.Service
}

func (h *Sim) Command() string     { return "/simangsuran" }
func (h *Sim) Description() string { return "Simulasi angsuran minimal nasabah berdasarkan SPK" }

func (h *Sim) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.Fields(msg.Text)
	if len(parts) < 2 {
		_, _ = b.SendText(chatID, "❌ *Format salah.*\nGunakan: `/simangsuran <no_spk>`")
		return
	}
	spk := strings.TrimSpace(parts[1])
	bills, err := h.Bills.GetByID(ctx, spk)
	if err != nil || bills == nil {
		_, _ = b.SendText(chatID, "❌ *SPK tidak ditemukan:* `"+spk+"`")
		return
	}
	res := simulasiangsuran.Hitung(bills)
	_, _ = b.SendText(chatID, simulasiangsuran.FormatTelegram(res, bills))
}
