// Package callbackhandler berisi handler untuk inline button callback.
package callbackhandler

import (
	"context"
	"fmt"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	cmdh "github.com/hikari-work/cek-pelunasan/internal/platform/telegram/commandhandler"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
)

// SelectBranch menangani callback dengan prefix "branch". Format data:
// "branch_<branch>_<name>". Render halaman pertama daftar nasabah.
type SelectBranch struct {
	Bills *bill.Service
}

func (h *SelectBranch) Prefix() string { return "branch" }

func (h *SelectBranch) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	branch, name := parts[1], parts[2]
	chatID := q.Message.Chat.ID

	page, err := h.Bills.FindByNameAndBranch(ctx, name, branch, 0, 5)
	if err != nil || len(page.Items) == 0 {
		editNotFound(b, chatID, q.Message.MessageID)
		return
	}
	text, kb := cmdh.BuildBillsListView(page, name, branch, 0)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// PagingBills menangani callback "paging_<name>_<branch>_<page>".
type PagingBills struct {
	Bills *bill.Service
}

func (h *PagingBills) Prefix() string { return "paging" }

func (h *PagingBills) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 4)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	name, branch := parts[1], parts[2]
	pageNum, err := parsePageNum(parts[3])
	if err != nil {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID

	page, err := h.Bills.FindByNameAndBranch(ctx, name, branch, pageNum, 5)
	if err != nil || len(page.Items) == 0 {
		answerNotFound(b, q.ID)
		return
	}
	text, kb := cmdh.BuildBillsListView(page, name, branch, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// Tagihan menangani callback "tagihan_<spk>_<name>_<branch>_<page>" — render
// detail satu tagihan + tombol Kembali ke list.
type Tagihan struct {
	Bills *bill.Service
}

func (h *Tagihan) Prefix() string { return "tagihan" }

func (h *Tagihan) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 5)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	spk, name, branch, pageStr := parts[1], parts[2], parts[3], parts[4]
	chatID := q.Message.Chat.ID

	bills, err := h.Bills.GetByID(ctx, spk)
	if err != nil || bills == nil {
		editNotFound(b, chatID, q.Message.MessageID)
		return
	}
	text := h.Bills.DetailMarkdown(ctx, bills)
	kb := tgbotapi.NewInlineKeyboardMarkup(tgbotapi.NewInlineKeyboardRow(
		tgbotapi.NewInlineKeyboardButtonData("◀️ Kembali",
			fmt.Sprintf("paging_%s_%s_%s", name, branch, pageStr)),
	))
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}
