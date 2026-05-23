package callbackhandler

import (
	"context"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	cmdh "github.com/hikari-work/cek-pelunasan/internal/platform/telegram/commandhandler"
	"github.com/hikari-work/cek-pelunasan/internal/service/credithistory"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
)

// SavingsBranchPick menangani callback "savingsBranch_<branch>_<name>".
type SavingsBranchPick struct {
	Savings *savings.Service
}

func (h *SavingsBranchPick) Prefix() string { return "savingsBranch" }

func (h *SavingsBranchPick) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	branch, name := parts[1], parts[2]
	page, err := h.Savings.FindByNameAndBranch(ctx, name, branch, 0)
	chatID := q.Message.Chat.ID
	if err != nil || len(page.Items) == 0 {
		editNotFound(b, chatID, q.Message.MessageID)
		return
	}
	text := h.Savings.FormatPage(ctx, page, 0)
	kb := buildSavingsPaginationKB(page, name, branch)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// SavingsPaginate menangani callback "savingsNext_<branch>_<name>_<page>".
type SavingsPaginate struct {
	Savings *savings.Service
}

func (h *SavingsPaginate) Prefix() string { return "savingsNext" }

func (h *SavingsPaginate) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 4)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	branch, name := parts[1], parts[2]
	pageNum, err := parsePageNum(parts[3])
	if err != nil {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID
	page, err := h.Savings.FindByNameAndBranch(ctx, name, branch, pageNum)
	if err != nil || len(page.Items) == 0 {
		answerNotFound(b, q.ID)
		return
	}
	text := h.Savings.FormatPage(ctx, page, 0)
	kb := buildSavingsPaginationKB(page, name, branch)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

func buildSavingsPaginationKB(page savings.PageResult, name, branch string) tgbotapi.InlineKeyboardMarkup {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
	}
	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if page.Page > 0 {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
				"savingsNext_"+branch+"_"+name+"_"+strconv.FormatInt(page.Page-1, 10)))
	}
	first := page.Page*page.Size + 1
	last := page.Page*page.Size + int64(len(page.Items))
	row = append(row,
		tgbotapi.NewInlineKeyboardButtonData(
			strconv.FormatInt(first, 10)+" - "+strconv.FormatInt(last, 10)+" / "+strconv.FormatInt(page.Total, 10), "none"))
	if page.Page+1 < totalPages {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("Next ➡",
				"savingsNext_"+branch+"_"+name+"_"+strconv.FormatInt(page.Page+1, 10)))
	}
	return tgbotapi.NewInlineKeyboardMarkup(row)
}

// CanvasPaginate menangani callback "canvas_<address>_<page>".
type CanvasPaginate struct {
	Savings *savings.Service
}

func (h *CanvasPaginate) Prefix() string { return "canvas" }

func (h *CanvasPaginate) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	address := parts[1]
	pageNum, err := parsePageNum(parts[2])
	if err != nil {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID
	keywords := strings.Fields(strings.ReplaceAll(address, ",", " "))
	page, err := h.Savings.FindFiltered(ctx, keywords, pageNum, 5)
	if err != nil || len(page.Items) == 0 {
		answerNotFound(b, q.ID)
		return
	}
	text, kb := cmdh.BuildCanvasView(page, address, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// CanvasingPaginate menangani callback "namaTagihan_<address>_<page>".
type CanvasingPaginate struct {
	History *credithistory.Service
}

func (h *CanvasingPaginate) Prefix() string { return "namaTagihan" }

func (h *CanvasingPaginate) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	address := parts[1]
	pageNum, err := parsePageNum(parts[2])
	if err != nil {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID
	keywords := strings.Fields(address)
	page, err := h.History.SearchAddressByKeywords(ctx, keywords, pageNum)
	if err != nil || len(page.Items) == 0 {
		answerNotFound(b, q.ID)
		return
	}
	text, kb := cmdh.BuildCanvasingView(page, address, pageNum)
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}
