package commandhandler

import (
	"context"
	"fmt"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/credithistory"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Canvasing /canvasing <alamat...> — cari mantan nasabah (credit_history)
// yang alamatnya match keyword dan tidak aktif lagi.
type Canvasing struct {
	History *credithistory.Service
}

func (h *Canvasing) Command() string { return "/canvasing" }
func (h *Canvasing) Description() string {
	return "Mengembalikan list nasabah yang pernah kredit namun tidak ambil lagi"
}

func (h *Canvasing) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	rest := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(msg.Text), "/canvasing"))
	if rest == "" {
		_, _ = b.SendText(chatID, "Alamat Harus Diisi")
		return
	}
	keywords := strings.Fields(rest)
	page, err := h.History.SearchAddressByKeywords(ctx, keywords, 0)
	if err != nil || len(page.Items) == 0 {
		_, _ = b.SendText(chatID, fmt.Sprintf("Data dengan alamat %s Tidak Ditemukan\n", rest))
		return
	}
	text, kb := buildCanvasingView(page, rest, 0)
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
}

// buildCanvasingView format pesan + tombol pagination.
// Data callback: "namaTagihan_<address>_<page>".
func buildCanvasingView(page credithistory.PageResult, address string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var sb strings.Builder
	fmt.Fprintf(&sb, "📄 Halaman %d dari %d\n\n", currentPage+1, totalPages)
	for i := range page.Items {
		sb.WriteString(formatCanvasingItem(&page.Items[i]))
	}

	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if currentPage > 0 {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
				fmt.Sprintf("namaTagihan_%s_%d", address, currentPage-1)))
	}
	first := currentPage*page.Size + 1
	last := currentPage*page.Size + int64(len(page.Items))
	row = append(row,
		tgbotapi.NewInlineKeyboardButtonData(
			fmt.Sprintf("%d - %d / %d", first, last, page.Total), "none"))
	if currentPage+1 < totalPages {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("Next ➡",
				fmt.Sprintf("namaTagihan_%s_%d", address, currentPage+1)))
	}
	return sb.String(), tgbotapi.NewInlineKeyboardMarkup(row)
}

func formatCanvasingItem(h *entity.CreditHistory) string {
	addr := h.Address
	if len(addr) > 35 {
		addr = addr[:32] + "..."
	}
	name := strings.ToUpper(h.Name)
	return fmt.Sprintf(`👤 *%s*
 ╔═══════════════════════
 ║ 📊 *DATA NASABAH*
 ║ ├─── 🆔 CIF   : `+"`%s`"+`
 ║ ├─── 📍 Alamat : %s
 ║ └─── 📱 Kontak : %s
 ╚═══════════════════════

`, name, h.CustomerID, addr, utils.FormatPhoneNumber(h.Phone))
}

// BuildCanvasingView dipakai callback handler.
func BuildCanvasingView(page credithistory.PageResult, address string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	return buildCanvasingView(page, address, currentPage)
}

// _ to keep context import used in signature for future.
var _ = context.TODO
