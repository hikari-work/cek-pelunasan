package callbackhandler

import (
	"context"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

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
	page, ok := cmdh.FetchMinimalPay(ctx, h.Bills, user, pageNum)
	if !ok || len(page.Items) == 0 {
		_ = b.AnswerCallback(q.ID, "Data tidak ditemukan")
		return
	}
	text, kb := cmdh.BuildMinimalPayView(page, user.UserCode, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// KolektasPaginate menangani callback "kolektas_<kelompok>_<page-1based>".
type KolektasPaginate struct {
	Service *kolektas.Service
}

func (h *KolektasPaginate) Prefix() string { return "kolektas" }

func (h *KolektasPaginate) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 3)
	if len(parts) < 3 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	kelompok := parts[1]
	pageNum, err := strconv.ParseInt(parts[2], 10, 64)
	if err != nil || pageNum < 1 {
		_ = b.AnswerCallback(q.ID, "Halaman tidak valid")
		return
	}
	chatID := q.Message.Chat.ID
	page, err := h.Service.FindByKelompok(ctx, kelompok, pageNum, 5)
	if err != nil || len(page.Items) == 0 {
		_ = b.AnswerCallback(q.ID, "Data tidak ditemukan")
		return
	}
	text, kb := cmdh.BuildKolektasView(page, kelompok)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}
