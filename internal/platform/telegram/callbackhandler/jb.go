package callbackhandler

import (
	"context"
	"strconv"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	cmdh "github.com/hikari-work/cek-pelunasan/internal/platform/telegram/commandhandler"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// TagihNext menangani callback "tagihNext_<userCode>_<page>" untuk fitur /jb.
// Filter dipilih ulang berdasarkan role user (AO/PIMP/ADMIN).
type TagihNext struct {
	Bills *bill.Service
	Users *users.Service
}

func (h *TagihNext) Prefix() string { return "tagihNext" }

func (h *TagihNext) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 3)
	if len(parts) < 3 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	pageNum, err := strconv.ParseInt(parts[2], 10, 64)
	if err != nil {
		_ = b.AnswerCallback(q.ID, "Halaman tidak valid")
		return
	}
	chatID := q.Message.Chat.ID

	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		_ = b.AnswerCallback(q.ID, "User tidak ditemukan")
		return
	}
	today := utils.DayOfMonth(time.Now().In(logsvc.JakartaTZ))
	page, ok := cmdh.FetchJB(ctx, h.Bills, user, today, pageNum)
	if !ok || len(page.Items) == 0 {
		_ = b.AnswerCallback(q.ID, "Data tidak ditemukan")
		return
	}
	text, kb := cmdh.BuildJBView(page, user.UserCode, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// suppress unused imports.
var _ = entity.RoleAO
