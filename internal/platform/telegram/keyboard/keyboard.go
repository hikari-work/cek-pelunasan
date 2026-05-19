// Package keyboard membangun inline keyboard yang dipakai banyak handler
// command/callback. Tidak ber-state; tiap fungsi hanya transform input ke
// markup Telegram.
package keyboard

import (
	"fmt"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
)

var monthLabelsID = [12]string{
	"Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
	"Jul", "Agu", "Sep", "Okt", "Nov", "Des",
}

// SlikMonthPicker bangun keyboard pilih bulan/tahun untuk /slik dan /doc.
// Format callback: "slikMonth_YYYYMM" — sengaja samakan dengan prefix yang
// terdaftar di router supaya callback handler nanti tinggal parse YYYYMM.
//
// monthsToShow boleh 0 — default 12 bulan (1 tahun terakhir).
func SlikMonthPicker(monthsToShow int) tgbotapi.InlineKeyboardMarkup {
	if monthsToShow <= 0 {
		monthsToShow = 12
	}
	now := time.Now().In(time.FixedZone("WIB", 7*3600))
	buttons := make([]tgbotapi.InlineKeyboardButton, 0, monthsToShow)
	for i := 0; i < monthsToShow; i++ {
		d := now.AddDate(0, -i, 0)
		yyyymm := fmt.Sprintf("%04d%02d", d.Year(), int(d.Month()))
		label := fmt.Sprintf("%s %d", monthLabelsID[int(d.Month())-1], d.Year())
		buttons = append(buttons, tgbotapi.NewInlineKeyboardButtonData(label, "slikMonth_"+yyyymm))
	}

	const cols = 3
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, (len(buttons)+cols-1)/cols)
	for i := 0; i < len(buttons); i += cols {
		end := i + cols
		if end > len(buttons) {
			end = len(buttons)
		}
		rows = append(rows, buttons[i:end])
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

// MinBungaCalendar bangun keyboard kalender pemilihan tanggal untuk /minbunga.
//
// identifier   = userCode (untuk AO) atau "<userCode>:<branch>" (untuk PIMP/ADMIN).
// selected     = daftar tanggal terpilih dalam format "YYYY-MM-DD".
// hasSelection = true kalau ingin tampilkan tombol Konfirmasi/Hapus.
//
// Callback format mengikuti legacy:
//   - "minbungaCal_<id>_YYYY-MM-DD"
//   - "minbungaConfirm_<id>"
//   - "minbungaClear_<id>"
//   - "none" untuk sel kosong/header
func MinBungaCalendar(identifier string, selected []string, hasSelection bool) tgbotapi.InlineKeyboardMarkup {
	wib := time.FixedZone("WIB", 7*3600)
	today := time.Now().In(wib)
	first := time.Date(today.Year(), today.Month(), 1, 0, 0, 0, 0, wib)
	lastDay := first.AddDate(0, 1, -1).Day()

	rows := make([][]tgbotapi.InlineKeyboardButton, 0, 8)

	header := fmt.Sprintf("%s %d", indonesianMonth(today.Month()), today.Year())
	rows = append(rows, tgbotapi.NewInlineKeyboardRow(
		tgbotapi.NewInlineKeyboardButtonData(header, "none")))

	dayNames := [7]string{"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"}
	dayRow := make([]tgbotapi.InlineKeyboardButton, 7)
	for i, n := range dayNames {
		dayRow[i] = tgbotapi.NewInlineKeyboardButtonData(n, "none")
	}
	rows = append(rows, dayRow)

	selectedSet := make(map[string]struct{}, len(selected))
	for _, s := range selected {
		selectedSet[s] = struct{}{}
	}

	startCol := int(first.Weekday()+6) % 7
	row := make([]tgbotapi.InlineKeyboardButton, 0, 7)
	for i := 0; i < startCol; i++ {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData(" ", "none"))
	}
	for day := 1; day <= lastDay; day++ {
		dateStr := fmt.Sprintf("%04d-%02d-%02d", today.Year(), int(today.Month()), day)
		label := fmt.Sprintf("%d", day)
		if _, ok := selectedSet[dateStr]; ok {
			label = "✅" + label
		}
		row = append(row, tgbotapi.NewInlineKeyboardButtonData(
			label, fmt.Sprintf("minbungaCal_%s_%s", identifier, dateStr)))
		if len(row) == 7 {
			rows = append(rows, row)
			row = make([]tgbotapi.InlineKeyboardButton, 0, 7)
		}
	}
	if len(row) > 0 {
		for len(row) < 7 {
			row = append(row, tgbotapi.NewInlineKeyboardButtonData(" ", "none"))
		}
		rows = append(rows, row)
	}

	if hasSelection {
		rows = append(rows, tgbotapi.NewInlineKeyboardRow(
			tgbotapi.NewInlineKeyboardButtonData("✅ Konfirmasi", "minbungaConfirm_"+identifier),
			tgbotapi.NewInlineKeyboardButtonData("🗑 Hapus Pilihan", "minbungaClear_"+identifier),
		))
	}

	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

// MinBungaBranchPicker bangun keyboard pilih cabang untuk role PIMP/ADMIN.
// Callback format: "minbunga_<branch>".
func MinBungaBranchPicker(branches []string) tgbotapi.InlineKeyboardMarkup {
	const cols = 3
	rows := make([][]tgbotapi.InlineKeyboardButton, 0)
	row := make([]tgbotapi.InlineKeyboardButton, 0, cols)
	for _, br := range branches {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData(br, "minbunga_"+br))
		if len(row) == cols {
			rows = append(rows, row)
			row = make([]tgbotapi.InlineKeyboardButton, 0, cols)
		}
	}
	if len(row) > 0 {
		rows = append(rows, row)
	}
	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}

func indonesianMonth(m time.Month) string {
	names := [...]string{
		"Januari", "Februari", "Maret", "April", "Mei", "Juni",
		"Juli", "Agustus", "September", "Oktober", "November", "Desember",
	}
	return names[int(m)-1]
}
