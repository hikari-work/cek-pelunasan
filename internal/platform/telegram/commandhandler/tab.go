package commandhandler

import (
	"context"
	"fmt"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// Tab /tab <nama> — cari rekening tabungan berdasarkan nama. Filter cabang
// otomatis kalau user sudah punya kode cabang. Kalau belum, tampilkan
// branch picker dulu.
type Tab struct {
	Savings *savings.Service
	Users   *users.Service
}

func (h *Tab) Command() string     { return "/tab" }
func (h *Tab) Description() string { return "Cari tabungan berdasarkan nama nasabah" }

func (h *Tab) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.SplitN(strings.TrimSpace(msg.Text), " ", 2)
	if len(parts) < 2 || strings.TrimSpace(parts[1]) == "" {
		_, _ = b.SendText(chatID, "Nama Harus Diisi")
		return
	}
	name := strings.TrimSpace(parts[1])

	branch, _ := h.Users.FindBranch(ctx, chatID)
	if branch != "" {
		start := time.Now()
		page, err := h.Savings.FindByNameAndBranch(ctx, name, branch, 0)
		if err != nil || len(page.Items) == 0 {
			_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
			return
		}
		text := h.Savings.FormatPage(ctx, page, time.Since(start).Milliseconds())
		kb := buildSavingsKeyboard(page, name, branch)
		_, _ = b.SendTextWithKeyboard(chatID, text, kb)
		return
	}

	branches, err := h.Savings.ListBranches(ctx, name)
	if err != nil || len(branches) == 0 {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}
	kb := savingsBranchPickerKeyboard(branches, name)
	_, _ = b.SendTextWithKeyboard(chatID,
		"Data ditemukan dalam beberapa cabang, pilih cabang:", kb)
}

// buildSavingsKeyboard bangun keyboard pagination untuk daftar tabungan.
// Data callback: "savingsNext_<branch>_<name>_<page>".
func buildSavingsKeyboard(page savings.PageResult, name, branch string) tgbotapi.InlineKeyboardMarkup {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
	}
	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if page.Page > 0 {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
				fmt.Sprintf("savingsNext_%s_%s_%d", branch, name, page.Page-1)))
	}
	first := page.Page*page.Size + 1
	last := page.Page*page.Size + int64(len(page.Items))
	row = append(row,
		tgbotapi.NewInlineKeyboardButtonData(
			fmt.Sprintf("%d - %d / %d", first, last, page.Total), "none"))
	if page.Page+1 < totalPages {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("Next ➡",
				fmt.Sprintf("savingsNext_%s_%s_%d", branch, name, page.Page+1)))
	}
	return tgbotapi.NewInlineKeyboardMarkup(row)
}

// savingsBranchPickerKeyboard tombol cabang 3-per-baris.
// Data callback: "savingsBranch_<branch>_<name>".
func savingsBranchPickerKeyboard(branches []string, name string) tgbotapi.InlineKeyboardMarkup {
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, (len(branches)+2)/3)
	current := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	for i, br := range branches {
		current = append(current, tgbotapi.NewInlineKeyboardButtonData(br, "savingsBranch_"+br+"_"+name))
		if len(current) == 3 || i == len(branches)-1 {
			rows = append(rows, current)
			current = make([]tgbotapi.InlineKeyboardButton, 0, 3)
		}
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}
