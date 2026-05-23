package commandhandler

import (
	"context"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
)

// MiniApp mengirim tombol URL yang membuka miniapp.
//
// Catatan tgbotapi v5.5.1 belum punya InlineKeyboardButtonWebApp. Fallback
// pakai NewInlineKeyboardButtonURL — user tetap bisa klik link untuk buka
// halaman mini app. Saat library di-upgrade dan punya WebApp button, ganti
// ke `NewInlineKeyboardButtonWebApp` agar mini app dibuka in-app oleh
// Telegram.
type MiniApp struct {
	URL string
}

func (h *MiniApp) Command() string     { return "/app" }
func (h *MiniApp) Description() string { return "Buka aplikasi Mini App." }

func (h *MiniApp) Handle(_ context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	if strings.TrimSpace(h.URL) == "" {
		_, _ = b.SendText(chatID, "Mini App belum dikonfigurasi. Hubungi administrator.")
		return
	}
	btn := tgbotapi.NewInlineKeyboardButtonURL("📱 Buka Aplikasi", h.URL)
	kb := tgbotapi.NewInlineKeyboardMarkup(tgbotapi.NewInlineKeyboardRow(btn))
	_, _ = b.SendTextWithKeyboard(chatID,
		"Klik tombol di bawah untuk membuka aplikasi pencarian tagihan, pelunasan, dan tabungan:", kb)
}
