// Package commandhandler berisi handler untuk command bot Telegram.
// Tiap file = 1 handler (sesuai struktur legacy). Konstanta/teks pesan yang
// panjang dipisah ke variabel agar gampang diubah tanpa search-replace.
package commandhandler

import (
	"context"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Start menangani /start — sekedar PONG ke user yang sudah authorized,
// atau pesan unauthorized + instruksi /id ke yang belum.
type Start struct {
	Authed *auth.AuthorizedChats
}

func (h *Start) Command() string     { return "/start" }
func (h *Start) Description() string { return "Mengecek Bot Apakah Aktif" }

func (h *Start) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	if h.Authed.IsAuthorized(chatID) {
		_, _ = b.SendText(chatID, "👋 *PONG!!!*\n")
		return
	}
	_, _ = b.SendText(chatID, utils.MsgUnauthorized)
}

// ID dipakai sebagai fallback handler — siapapun bisa kirim "/id" untuk
// dapat chatId mereka, lalu kirim ke admin untuk diotorisasi.
type ID struct{}

func (h *ID) Command() string     { return "/id" }
func (h *ID) Description() string { return "Menampilkan chat ID kamu" }

func (h *ID) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	_, _ = b.SendText(msg.Chat.ID, "Chat ID kamu: `"+strconv.FormatInt(msg.Chat.ID, 10)+"`")
}

// Help: pesan ringkas + instruksi.
type Help struct{}

func (h *Help) Command() string     { return "/help" }
func (h *Help) Description() string { return "Menampilkan menu bantuan dan daftar fitur" }

func (h *Help) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	const text = `Bot ini dipakai untuk mencari tagihan dan pelunasan.

Perintah dasar:
- /start — cek bot aktif
- /id — dapatkan chat ID kamu
- /status — status sistem (admin/AO/PIMP)
- /tagih — cari tagihan (admin/AO/PIMP)
- /slik — cari/upload SLIK
- /minbunga — tagihan minimal bunga
- /canvas — canvasing tabungan

Untuk daftar lengkap, hubungi admin.`
	_, _ = b.SendText(msg.Chat.ID, text)
}

// IsNumber helper — argumen string punya format angka.
func IsNumber(s string) bool {
	_, err := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
	return err == nil
}
