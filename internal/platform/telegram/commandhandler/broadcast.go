package commandhandler

import (
	"context"
	"log/slog"
	"strconv"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// Broadcast /broadcast — admin reply pesan, lalu /broadcast → salin pesan
// itu ke semua user terdaftar. Padanan BroadcastCommandHandler.
//
// Hanya admin (chatID == OwnerID) yang dibolehkan.
type Broadcast struct {
	OwnerID int64
	Users   *users.Service
}

func (h *Broadcast) Command() string     { return "/broadcast" }
func (h *Broadcast) Description() string { return "Broadcast pesan ke semua user (admin)" }

func (h *Broadcast) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	if h.OwnerID != 0 && chatID != h.OwnerID {
		_, _ = b.SendText(chatID, "Hanya admin yang dapat menjalankan perintah ini.")
		return
	}
	if msg.ReplyToMessage == nil {
		_, _ = b.SendText(chatID,
			"❗ *Format salah.*\nBalas pesan yang mau di-broadcast, lalu ketik `/broadcast`")
		return
	}
	all, err := h.Users.FindAll(ctx)
	if err != nil {
		_, _ = b.SendText(chatID, "❗ Gagal mengambil daftar user.")
		return
	}
	go func() {
		count := 0
		for _, u := range all {
			copyMsg := tgbotapi.NewCopyMessage(u.ChatID, chatID, msg.ReplyToMessage.MessageID)
			if _, err := b.API.Request(copyMsg); err != nil {
				slog.Warn("broadcast copy failed", "to", u.ChatID, "err", err)
			} else {
				count++
			}
			time.Sleep(500 * time.Millisecond)
		}
		_, _ = b.SendText(chatID,
			"✅ Broadcast selesai ke "+strconv.Itoa(count)+" pengguna.")
	}()
}
