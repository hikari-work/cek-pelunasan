package commandhandler

import (
	"context"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Dauth /deauth <chatId> — admin mencabut akses bot dari user.
// Padanan DeleteUserAccessCommand.
type Dauth struct {
	OwnerID int64
	Authed  *auth.AuthorizedChats
	Users   *users.Service
}

func (h *Dauth) Command() string     { return "/deauth" }
func (h *Dauth) Description() string { return "Gunakan Command ini untuk menghapus izin user." }

func (h *Dauth) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.Fields(msg.Text)
	if len(parts) < 2 {
		_, _ = b.SendText(chatID, utils.MsgInvalidDeauthFormat)
		return
	}
	target, err := strconv.ParseInt(parts[1], 10, 64)
	if err != nil {
		_, _ = b.SendText(chatID, utils.MsgIDMustBeNumber)
		return
	}
	if err := h.Users.Delete(ctx, target); err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal menghapus user.")
		return
	}
	h.Authed.RemoveChat(target)
	_, _ = b.SendText(target, utils.MsgUnauthorized)
	if h.OwnerID != 0 {
		_, _ = b.SendText(h.OwnerID, "Sukses")
	} else {
		_, _ = b.SendText(chatID, "Sukses")
	}
}
