package callbackhandler

import (
	"context"
	"errors"
	"strconv"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
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

// UserPaginationHandler handleUserPaginationCallback adalah generic handler untuk pagination yang memerlukan user lookup.
// Digunakan untuk pattern: parse callback -> get user -> fetch page -> render view.
type UserPaginationHandler struct {
	Users     *users.Service
	FetchPage func(ctx context.Context, user *entity.User, pageNum int64) (string, tgbotapi.InlineKeyboardMarkup, bool)
}

func (h *UserPaginationHandler) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts, err := parseCallbackParts(q.Data, 3)
	if err != nil {
		answerInvalid(b, q.ID)
		return
	}
	pageNum, err := parsePageNum(parts[2])
	if err != nil {
		answerInvalidPage(b, q.ID)
		return
	}
	chatID := q.Message.Chat.ID

	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		answerUserNotFound(b, q.ID)
		return
	}
	text, kb, ok := h.FetchPage(ctx, user, pageNum)
	if !ok {
		answerNotFound(b, q.ID)
		return
	}
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}
