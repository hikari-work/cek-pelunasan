package callbackhandler

import (
	"context"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	cmdh "github.com/hikari-work/cek-pelunasan/internal/platform/telegram/commandhandler"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/kolektas"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// MinimalPayPaginate menangani callback "minimalpay_<userCode>_<page>".
type MinimalPayPaginate struct {
	Bills *bill.Service
	Users *users.Service
}

func (h *MinimalPayPaginate) Prefix() string { return "minimalpay" }

func (h *MinimalPayPaginate) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	handler := &UserPaginationHandler{
		Users: h.Users,
		FetchPage: func(ctx context.Context, user *entity.User, pageNum int64) (string, tgbotapi.InlineKeyboardMarkup, bool) {
			page, ok := cmdh.FetchMinimalPay(ctx, h.Bills, user, pageNum)
			if !ok || len(page.Items) == 0 {
				return "", tgbotapi.InlineKeyboardMarkup{}, false
			}
			text, kb := cmdh.BuildMinimalPayView(page, user.UserCode, pageNum)
			return text, kb, true
		},
	}
	handler.Handle(ctx, b, q)
}

// KolektasPaginate menangani callback "kolektas_<kelompok>_<page-1based>".
type KolektasPaginate struct {
	Service *kolektas.Service
}

func (h *KolektasPaginate) Prefix() string { return "kolektas" }

func (h *KolektasPaginate) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	kelompok := parts[1]
	pageNum, err := parsePageNum(parts[2])
	if err != nil || pageNum < 1 {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID
	page, err := h.Service.FindByKelompok(ctx, kelompok, pageNum, 5)
	if err != nil || len(page.Items) == 0 {
		answerNotFound(b, q.ID)
		return
	}
	text, kb := cmdh.BuildKolektasView(page, kelompok)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}
