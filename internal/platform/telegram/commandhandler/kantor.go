package commandhandler

import (
	"context"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// Kantor /kantor — lihat/ubah kode cabang user.
//   - tanpa argumen: tampilkan branch saat ini
//   - /kantor <kode-4digit>: simpan branch baru
type Kantor struct {
	Users *users.Service
}

func (h *Kantor) Command() string     { return "/kantor" }
func (h *Kantor) Description() string { return "Melihat atau mengubah kode kantor (4 digit)" }

func (h *Kantor) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	text := strings.TrimSpace(msg.Text)
	if text == "/kantor" {
		branch, err := h.Users.FindBranch(ctx, chatID)
		if err != nil {
			_, _ = b.SendText(chatID, "❌ Gagal mengambil data kantor.")
			return
		}
		if branch == "" {
			_, _ = b.SendText(chatID, "Anda Tidak terdaftar di kantor manapun")
			return
		}
		_, _ = b.SendText(chatID, "Anda sebelumnya terdaftar di kantor "+branch)
		return
	}

	kantor := strings.TrimSpace(strings.TrimPrefix(text, "/kantor"))
	if len(kantor) != 4 {
		_, _ = b.SendText(chatID, "Format Kantor Tidak tepat!!!")
		return
	}
	if err := h.Users.SaveBranch(ctx, chatID, kantor); err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal menyimpan kantor.")
		return
	}
	_, _ = b.SendText(chatID, "Sukses mengubah kantor anda menjadi "+kantor)
}
