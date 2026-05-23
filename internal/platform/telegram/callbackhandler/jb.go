package callbackhandler

import (
	"context"
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
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	pageNum, err := parsePageNum(parts[2])
	if err != nil {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID

	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		answerUserNotFound(b, q.ID)
		return
	}
	today := utils.DayOfMonth(time.Now().In(logsvc.JakartaTZ))
	page, ok := cmdh.FetchJB(ctx, h.Bills, user, today, pageNum)
	if !ok || len(page.Items) == 0 {
		answerNotFound(b, q.ID)
		return
	}
	text, kb := cmdh.BuildJBView(page, user.UserCode, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// suppress unused imports.
var _ = entity.RoleAO
