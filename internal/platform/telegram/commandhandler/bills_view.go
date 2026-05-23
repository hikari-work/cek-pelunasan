package commandhandler

import (
	"fmt"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// BuildBillsListView merender pesan daftar nasabah + inline keyboard
// dengan tombol pagination dan tombol nama (data callback "tagihan_<spk>_<name>_<branch>_<page>").
//
// Padanan ButtonListForBills.dynamicButtonName + buildMessage di
// SelectBranchCallbackHandler. Dipakai oleh command (/cariNasabah)
// dan oleh callback (branch picker, tombol Kembali, paging).
func BuildBillsListView(
	page bill.PageResult[entity.Bills],
	name, branch string,
	currentPage int64,
) (string, tgbotapi.InlineKeyboardMarkup) {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("🏦 *DAFTAR NASABAH*\n══════════════════\n📋 Halaman %d dari %d\n",
		currentPage+1, totalPages))
	for _, b := range page.Items {
		sb.WriteString(fmt.Sprintf(`
🔷 *%s*
────────────────
📎 *Detail Nasabah*
▪️ ID SPK		: `+"`%s`"+`
▪️ Alamat		: %s

💰 *Informasi Kredit*
▪️ Plafond		: %s
▪️ AO			: %s
────────────────
`,
			b.Name,
			b.NoSpk,
			b.Address,
			utils.FormatRupiah(b.Plafond),
			b.AccountOfficer,
		))
	}

	kb := buildBillsKeyboard(page, name, branch, currentPage, totalPages)
	return sb.String(), kb
}

func buildBillsKeyboard(page bill.PageResult[entity.Bills], name, branch string, currentPage, totalPages int64) tgbotapi.InlineKeyboardMarkup {
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, 4)

	// Baris pagination.
	pagingRow := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if currentPage > 0 {
		pagingRow = append(pagingRow,
			tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
				fmt.Sprintf("paging_%s_%s_%d", name, branch, currentPage-1)))
	}
	first := currentPage*page.Size + 1
	last := currentPage*page.Size + int64(len(page.Items))
	pagingRow = append(pagingRow,
		tgbotapi.NewInlineKeyboardButtonData(
			fmt.Sprintf("%d - %d / %d", first, last, page.Total),
			"none"))
	if currentPage+1 < totalPages {
		pagingRow = append(pagingRow,
			tgbotapi.NewInlineKeyboardButtonData("Next ➡",
				fmt.Sprintf("paging_%s_%s_%d", name, branch, currentPage+1)))
	}
	rows = append(rows, pagingRow)

	// Baris nama (2 per baris).
	current := make([]tgbotapi.InlineKeyboardButton, 0, 2)
	for i, b := range page.Items {
		current = append(current, tgbotapi.NewInlineKeyboardButtonData(
			truncate(b.Name, 28),
			fmt.Sprintf("tagihan_%s_%s_%s_%d", b.NoSpk, name, branch, currentPage),
		))
		if len(current) == 2 || i == len(page.Items)-1 {
			rows = append(rows, current)
			current = make([]tgbotapi.InlineKeyboardButton, 0, 2)
		}
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n-1] + "…"
}