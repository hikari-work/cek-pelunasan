package commandhandler

import (
	"context"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Auth Auth: admin pakai /auth <chatId> untuk mendaftarkan user baru.
type Auth struct {
	OwnerID int64
	Authed  *auth.AuthorizedChats
	Users   *users.Service
}

func (h *Auth) Command() string { return "/auth" }
func (h *Auth) Description() string {
	return "Gunakan command ini untuk memberikan izin kepada user untuk menggunakan bot."
}

func (h *Auth) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
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
	if err := h.Users.InsertNew(ctx, target); err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal menyimpan user.")
		return
	}
	h.Authed.AddChat(target)
	_, _ = b.SendText(target, utils.MsgAuthorizedSuccess)
	_, _ = b.SendText(chatID, "Sukses")
}

// Otor /otor <kode> — daftarkan user sebagai AO (kode 3 char) atau PIMP (kode cabang numerik).
type Otor struct {
	Users *users.Service
	Bills *bill.Service
}

func (h *Otor) Command() string     { return "/otor" }
func (h *Otor) Description() string { return "Mendaftarkan diri sebagai AO atau Pimpinan Cabang" }

func (h *Otor) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.Fields(msg.Text)
	if len(parts) < 2 {
		_, _ = b.SendText(chatID, "Gunakan /otor <kode cabang> atau\n/otor <kode ao>")
		return
	}
	target := parts[1]
	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		_, _ = b.SendText(chatID, "User tidak ditemukan")
		return
	}
	switch {
	case len(target) == 3:
		user.UserCode = target
		user.Roles = entity.RoleAO
		if err := h.Users.SaveBranch(ctx, chatID, user.Branch); err == nil {
			_, _ = b.SendText(chatID, "✅ User berhasil didaftarkan sebagai *AO*")
		}
	case IsNumber(target):
		branches, err := h.Bills.ListAllBranches(ctx)
		if err != nil {
			_, _ = b.SendText(chatID, "❌ Gagal memvalidasi cabang.")
			return
		}
		valid := false
		for _, br := range branches {
			if br == target {
				valid = true
				break
			}
		}
		if !valid {
			return
		}
		user.UserCode = target
		user.Roles = entity.RolePIMP
		_ = h.Users.SaveBranch(ctx, chatID, target)
		_, _ = b.SendText(chatID, "✅ User berhasil didaftarkan sebagai *Pimpinan*")
	default:
		_, _ = b.SendText(chatID, "❌ *Format tidak valid*\n\nContoh: /otor 1234567890")
	}
}
