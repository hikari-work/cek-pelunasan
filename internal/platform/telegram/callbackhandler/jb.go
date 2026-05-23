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
	today := utils.DayOfMonth(time.Now().In(logsvc.JakartaTZ))
	handler := &UserPaginationHandler{
		Users: h.Users,
		FetchPage: func(ctx context.Context, user *entity.User, pageNum int64) (string, tgbotapi.InlineKeyboardMarkup, bool) {
			page, ok := cmdh.FetchJB(ctx, h.Bills, user, today, pageNum)
			if !ok || len(page.Items) == 0 {
				return "", tgbotapi.InlineKeyboardMarkup{}, false
			}
			text, kb := cmdh.BuildJBView(page, user.UserCode, pageNum)
			return text, kb, true
		},
	}
	handler.Handle(ctx, b, q)
}

// suppress unused imports.
var _ = entity.RoleAO
