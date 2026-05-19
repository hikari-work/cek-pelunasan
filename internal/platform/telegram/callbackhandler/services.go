package callbackhandler

import (
	"context"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
)

// Services menangani callback "services_<Pelunasan|Tabungan>_<query>".
// Setelah user pilih jenis layanan dari /owner flow, kita tampilkan branch picker.
type Services struct {
	Bills   *bill.Service
	Savings *savings.Service
}

func (h *Services) Prefix() string { return "services" }

func (h *Services) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 3)
	if len(parts) < 3 {
		_ = b.AnswerCallback(q.ID, "Data tidak valid")
		return
	}
	service, query := parts[1], parts[2]
	chatID := q.Message.Chat.ID
	messageID := q.Message.MessageID
	_ = b.AnswerCallback(q.ID, "")

	switch service {
	case "Pelunasan":
		branches, err := h.Bills.ListAllBranches(ctx)
		if err != nil || len(branches) == 0 {
			_ = b.EditText(chatID, messageID, "❌ *Data tidak ditemukan*")
			return
		}
		kb := pelunasanBranchKB(branches, query)
		_ = b.EditTextWithMarkup(chatID, messageID,
			"🏦 *Pilih Cabang untuk Pelunasan*\n\nNasabah: *"+query+"*", kb)
	case "Tabungan":
		branches, err := h.Savings.ListBranches(ctx, query)
		if err != nil || len(branches) == 0 {
			_ = b.EditText(chatID, messageID, "❌ *Data tidak ditemukan*")
			return
		}
		kb := tabunganBranchKB(branches, query)
		_ = b.EditTextWithMarkup(chatID, messageID,
			"💰 *Pilih Cabang untuk Tabungan*\n\nNasabah: *"+query+"*", kb)
	default:
		_ = b.EditText(chatID, messageID, "❌ *Layanan tidak dikenali*")
	}
}

// pelunasanBranchKB pakai prefix "branch_" yang sudah ditangani SelectBranch handler
// di bills.go (paginate tagihan by name).
func pelunasanBranchKB(branches []string, query string) tgbotapi.InlineKeyboardMarkup {
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, (len(branches)+2)/3)
	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	for i, br := range branches {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData(br, "branch_"+br+"_"+query))
		if len(row) == 3 || i == len(branches)-1 {
			rows = append(rows, row)
			row = make([]tgbotapi.InlineKeyboardButton, 0, 3)
		}
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

// tabunganBranchKB pakai prefix "branchtab_" yang ditangani SavingsBranchSelect.
// Legacy max 4 per baris; kita ikuti.
func tabunganBranchKB(branches []string, query string) tgbotapi.InlineKeyboardMarkup {
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, (len(branches)+3)/4)
	row := make([]tgbotapi.InlineKeyboardButton, 0, 4)
	for i, br := range branches {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData(br, "branchtab_"+br+"_"+query))
		if len(row) == 4 || i == len(branches)-1 {
			rows = append(rows, row)
			row = make([]tgbotapi.InlineKeyboardButton, 0, 4)
		}
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

// SavingsBranchSelect menangani callback "branchtab_<branch>_<query>" — branch
// picker dari Services Tabungan, langsung load halaman 0 dari savings.
type SavingsBranchSelect struct {
	Savings *savings.Service
}

func (h *SavingsBranchSelect) Prefix() string { return "branchtab" }

func (h *SavingsBranchSelect) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 3)
	if len(parts) < 3 {
		_ = b.AnswerCallback(q.ID, "Data tidak valid")
		return
	}
	branch, query := parts[1], parts[2]
	chatID := q.Message.Chat.ID
	messageID := q.Message.MessageID
	_ = b.AnswerCallback(q.ID, "")

	start := time.Now()
	page, err := h.Savings.FindByNameAndBranch(ctx, query, branch, 0)
	if err != nil || len(page.Items) == 0 {
		_ = b.EditText(chatID, messageID, "❌ *Data tidak ditemukan*")
		return
	}
	text := h.Savings.FormatPage(ctx, page, time.Since(start).Milliseconds())
	kb := buildSavingsPaginationKB(page, query, branch)
	_ = b.EditTextWithMarkup(chatID, messageID, text, kb)
}
