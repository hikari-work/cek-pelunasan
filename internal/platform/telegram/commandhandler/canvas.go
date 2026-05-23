package commandhandler

import (
	"context"
	"fmt"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
)

// Canvas /canvas <alamat...> — cari tabungan yang alamatnya match semua keyword,
// kecuali yang masih punya kredit aktif. Padanan CanvasingTabCommandHandler.
type Canvas struct {
	Savings *savings.Service
}

func (h *Canvas) Command() string     { return "/canvas" }
func (h *Canvas) Description() string { return "Canvasing tabungan berdasarkan alamat" }

func (h *Canvas) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	rest := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(msg.Text), "/canvas"))
	if rest == "" {
		_, _ = b.SendText(chatID, "Format salah, silahkan gunakan /canvas <alamat>")
		return
	}
	keywords := splitCanvasKeywords(rest)
	page, err := h.Savings.FindFiltered(ctx, keywords, 0, 5)
	if err != nil {
		_, _ = b.SendText(chatID, "Tidak ada data yang ditemukan")
		return
	}
	if len(page.Items) == 0 {
		_, _ = b.SendText(chatID, "Tidak ada data yang ditemukan")
		return
	}
	text, kb := buildCanvasView(page, rest, 0)
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
}

func splitCanvasKeywords(s string) []string {
	var out []string
	for _, part := range strings.Split(s, ",") {
		for _, w := range strings.Fields(part) {
			w = strings.TrimSpace(w)
			if w != "" {
				out = append(out, w)
			}
		}
	}
	return out
}

// buildCanvasView render header + entri ringkas + tombol pagination.
// Data callback: "canvas_<address>_<page>".
func buildCanvasView(page savings.PageResult, address string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var sb strings.Builder
	fmt.Fprintf(&sb, "📊 *INFORMASI TABUNGAN*\n───────────────────\n📄 Halaman %d dari %d\n\n",
		currentPage+1, totalPages)
	for i := range page.Items {
		sb.WriteString(savings.FormatCanvas(&page.Items[i]))
	}

	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if currentPage > 0 {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
				fmt.Sprintf("canvas_%s_%d", address, currentPage-1)))
	}
	first := currentPage*page.Size + 1
	last := currentPage*page.Size + int64(len(page.Items))
	row = append(row,
		tgbotapi.NewInlineKeyboardButtonData(
			fmt.Sprintf("%d - %d / %d", first, last, page.Total), "none"))
	if currentPage+1 < totalPages {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("Next ➡",
				fmt.Sprintf("canvas_%s_%d", address, currentPage+1)))
	}
	return sb.String(), tgbotapi.NewInlineKeyboardMarkup(row)
}

// BuildCanvasView eksport untuk callback handler reuse.
func BuildCanvasView(page savings.PageResult, address string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	return buildCanvasView(page, address, currentPage)
}
