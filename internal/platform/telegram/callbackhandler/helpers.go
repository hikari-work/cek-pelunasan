package callbackhandler

import (
	"errors"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
)

// parseCallbackParts memparse callback data dengan delimiter "_" dan validasi jumlah parts.
// Mengembalikan error jika jumlah parts tidak sesuai expected.
func parseCallbackParts(data string, expected int) ([]string, error) {
	parts := strings.SplitN(data, "_", expected)
	if len(parts) < expected {
		return nil, errors.New("invalid callback data")
	}
	return parts, nil
}

// parsePageNum memparse string menjadi page number (int64).
// Mengembalikan error jika parsing gagal.
func parsePageNum(s string) (int64, error) {
	pageNum, err := strconv.ParseInt(s, 10, 64)
	if err != nil {
		return 0, errors.New("invalid page number")
	}
	return pageNum, nil
}

// answerInvalid mengirim callback answer dengan pesan "Data callback tidak valid".
func answerInvalid(b *telegram.Bot, callbackID string) {
	_ = b.AnswerCallback(callbackID, "Data callback tidak valid")
}

// answerInvalidPage mengirim callback answer dengan pesan "Halaman tidak valid".
func answerInvalidPage(b *telegram.Bot, callbackID string) {
	_ = b.AnswerCallback(callbackID, "Halaman tidak valid")
}

// answerNotFound mengirim callback answer dengan pesan "Data tidak ditemukan".
func answerNotFound(b *telegram.Bot, callbackID string) {
	_ = b.AnswerCallback(callbackID, "Data tidak ditemukan")
}

// answerUserNotFound mengirim callback answer dengan pesan "User tidak ditemukan".
func answerUserNotFound(b *telegram.Bot, callbackID string) {
	_ = b.AnswerCallback(callbackID, "User tidak ditemukan")
}

// editNotFound mengedit message dengan pesan "❌ *Data tidak ditemukan*".
func editNotFound(b *telegram.Bot, chatID int64, messageID int) {
	_ = b.EditText(chatID, messageID, "❌ *Data tidak ditemukan*")
}

// buildPaginationKeyboard membuat inline keyboard untuk pagination dengan tombol prev/next.
// page: struct dengan field Page, Size, Total (int64)
// prefix: callback prefix untuk tombol (e.g., "paging", "savingsNext")
// params: parameter tambahan untuk callback data (e.g., name, branch)
func buildPaginationKeyboard(page interface {
	GetPage() int64
	GetSize() int64
	GetTotal() int64
}, prefix string, params ...string) tgbotapi.InlineKeyboardMarkup {
	currentPage := page.GetPage()
	size := page.GetSize()
	total := page.GetTotal()

	totalPages := int64(0)
	if size > 0 {
		totalPages = (total + size - 1) / size
	}

	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)

	// Prev button
	if currentPage > 0 {
		callbackData := prefix
		for _, p := range params {
			callbackData += "_" + p
		}
		callbackData += "_" + strconv.FormatInt(currentPage-1, 10)
		row = append(row, tgbotapi.NewInlineKeyboardButtonData("⬅ Prev", callbackData))
	}

	// Current range indicator
	first := currentPage*size + 1
	last := currentPage*size + int64(size)
	if last > total {
		last = total
	}
	rangeText := strconv.FormatInt(first, 10) + " - " + strconv.FormatInt(last, 10) + " / " + strconv.FormatInt(total, 10)
	row = append(row, tgbotapi.NewInlineKeyboardButtonData(rangeText, "none"))

	// Next button
	if currentPage+1 < totalPages {
		callbackData := prefix
		for _, p := range params {
			callbackData += "_" + p
		}
		callbackData += "_" + strconv.FormatInt(currentPage+1, 10)
		row = append(row, tgbotapi.NewInlineKeyboardButtonData("Next ➡", callbackData))
	}

	return tgbotapi.NewInlineKeyboardMarkup(row)
}

// buildBranchKeyboard membuat inline keyboard untuk memilih branch.
// branches: list nama branch
// prefix: callback prefix (e.g., "branch", "branchtab")
// query: query string untuk callback data
// perRow: jumlah button per baris (biasanya 3 atau 4)
func buildBranchKeyboard(branches []string, prefix, query string, perRow int) tgbotapi.InlineKeyboardMarkup {
	rows := make([][]tgbotapi.InlineKeyboardButton, 0, (len(branches)+perRow-1)/perRow)
	row := make([]tgbotapi.InlineKeyboardButton, 0, perRow)

	for i, br := range branches {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData(br, prefix+"_"+br+"_"+query))
		if len(row) == perRow || i == len(branches)-1 {
			rows = append(rows, row)
			row = make([]tgbotapi.InlineKeyboardButton, 0, perRow)
		}
	}

	return tgbotapi.NewInlineKeyboardMarkup(rows...)
}
