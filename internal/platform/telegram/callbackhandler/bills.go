// Package callbackhandler berisi handler untuk inline button callback.
package callbackhandler

import (
	"context"
	"fmt"
	"strconv"
	"strings"

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
	parts := strings.SplitN(q.Data, "_", 3)
	if len(parts) < 3 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	branch, name := parts[1], parts[2]
	chatID := q.Message.Chat.ID

	page, err := h.Bills.FindByNameAndBranch(ctx, name, branch, 0, 5)
	if err != nil || len(page.Items) == 0 {
		_ = b.EditText(chatID, q.Message.MessageID, "❌ *Data tidak ditemukan*")
		return
	}
	text, kb := cmdh.BuildBillsListView(ctx, h.Bills, page, name, branch, 0)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// PagingBills menangani callback "paging_<name>_<branch>_<page>".
type PagingBills struct {
	Bills *bill.Service
}

func (h *PagingBills) Prefix() string { return "paging" }

func (h *PagingBills) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 4)
	if len(parts) < 4 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	name, branch := parts[1], parts[2]
	pageNum, err := strconv.ParseInt(parts[3], 10, 64)
	if err != nil {
		_ = b.AnswerCallback(q.ID, "Halaman tidak valid")
		return
	}
	chatID := q.Message.Chat.ID

	page, err := h.Bills.FindByNameAndBranch(ctx, name, branch, pageNum, 5)
	if err != nil || len(page.Items) == 0 {
		_ = b.AnswerCallback(q.ID, "Data tidak ditemukan")
		return
	}
	text, kb := cmdh.BuildBillsListView(ctx, h.Bills, page, name, branch, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// Tagihan menangani callback "tagihan_<spk>_<name>_<branch>_<page>" — render
// detail satu tagihan + tombol Kembali ke list.
type Tagihan struct {
	Bills *bill.Service
}

func (h *Tagihan) Prefix() string { return "tagihan" }

func (h *Tagihan) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 5)
	if len(parts) < 5 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	spk, name, branch, pageStr := parts[1], parts[2], parts[3], parts[4]
	chatID := q.Message.Chat.ID

	bills, err := h.Bills.GetByID(ctx, spk)
	if err != nil || bills == nil {
		_ = b.EditText(chatID, q.Message.MessageID, "❌ *Data tidak ditemukan*")
		return
	}
	text := h.Bills.DetailMarkdown(ctx, bills)
	kb := tgbotapi.NewInlineKeyboardMarkup(tgbotapi.NewInlineKeyboardRow(
		tgbotapi.NewInlineKeyboardButtonData("◀️ Kembali",
			fmt.Sprintf("paging_%s_%s_%s", name, branch, pageStr)),
	))
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}
