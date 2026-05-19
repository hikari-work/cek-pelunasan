package commandhandler

import (
	"context"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
)

// CariNasabah /tgnama <nama> — cari cabang yang punya nasabah dengan nama itu,
// lalu tampilkan inline keyboard branch picker. Pemilihan branch ditangani
// callback handler "branch" → SelectBranchCallback.
type CariNasabah struct {
	Bills *bill.Service
}

func (h *CariNasabah) Command() string     { return "/tgnama" }
func (h *CariNasabah) Description() string { return "Cari nasabah berdasarkan nama (lalu pilih cabang)" }

func (h *CariNasabah) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.SplitN(strings.TrimSpace(msg.Text), " ", 2)
	if len(parts) < 2 || strings.TrimSpace(parts[1]) == "" {
		_, _ = b.SendText(chatID, "❌ *Format tidak valid*\n\nContoh: /tgnama Budi")
		return
	}
	name := strings.TrimSpace(parts[1])

	// Daftar cabang yang punya nasabah dengan nama tsb. Pendekatan paling
	// hemat: cek pakai FindByName tanpa filter cabang, kumpulkan distinct branch.
	page, err := h.Bills.FindByName(ctx, name, 0, 200)
	if err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal mencari nasabah.")
		return
	}
	if len(page.Items) == 0 {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}

	branches := uniqueBranches(page.Items)
	if len(branches) == 1 {
		// Tidak ambigu — langsung tampilkan list pertama.
		showBills(ctx, b, h.Bills, chatID, name, branches[0])
		return
	}
	kb := branchPickerKeyboard(branches, name)
	_, _ = b.SendTextWithKeyboard(chatID,
		"⚠ *Terdapat lebih dari satu cabang dengan nama yang sama*\n\nSilakan pilih cabang yang sesuai:", kb)
}

func uniqueBranches(items []entity.Bills) []string {
	seen := make(map[string]struct{}, len(items))
	out := make([]string, 0, 8)
	for _, b := range items {
		if b.Branch == "" {
			continue
		}
		if _, ok := seen[b.Branch]; ok {
			continue
		}
		seen[b.Branch] = struct{}{}
		out = append(out, b.Branch)
	}
	return out
}

// branchPickerKeyboard padanan ButtonListForSelectBranch.dynamicSelectBranch.
// Tombol disusun 3 per baris. Data callback: "branch_<branch>_<name>".
func branchPickerKeyboard(branches []string, name string) tgbotapi.InlineKeyboardMarkup {
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, (len(branches)+2)/3)
	current := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	for i, br := range branches {
		current = append(current, tgbotapi.NewInlineKeyboardButtonData(br, "branch_"+br+"_"+name))
		if len(current) == 3 || i == len(branches)-1 {
			rows = append(rows, current)
			current = make([]tgbotapi.InlineKeyboardButton, 0, 3)
		}
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

// showBills langsung tampilkan halaman pertama daftar nasabah pada branch yang dipilih.
func showBills(ctx context.Context, b *telegram.Bot, bills *bill.Service, chatID int64, name, branch string) {
	page, err := bills.FindByNameAndBranch(ctx, name, branch, 0, 5)
	if err != nil || len(page.Items) == 0 {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}
	text, kb := BuildBillsListView(ctx, bills, page, name, branch, 0)
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
}
