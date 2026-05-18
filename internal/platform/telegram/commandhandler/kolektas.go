package commandhandler

import (
	"context"
	"fmt"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/kolektas"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Kolektas /kolektas <kelompok> — daftar kolek_tas per kelompok dengan pagination.
type Kolektas struct {
	Service *kolektas.Service
}

func (h *Kolektas) Command() string     { return "/kolektas" }
func (h *Kolektas) Description() string { return "Kolek Tas (kelompok)" }

func (h *Kolektas) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	parts := strings.SplitN(strings.TrimSpace(msg.Text), " ", 2)
	if len(parts) < 2 || strings.TrimSpace(parts[1]) == "" {
		_, _ = b.SendText(chatID, "Data Tidak Boleh Kosong")
		return
	}
	kelompok := strings.ToLower(strings.TrimSpace(parts[1]))
	page, err := h.Service.FindByKelompok(ctx, kelompok, 1, 5)
	if err != nil || len(page.Items) == 0 {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}
	text, kb := buildKolektasView(page, kelompok)
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
}

// buildKolektasView render entri + tombol pagination "kolektas_<kelompok>_<page1based>".
func buildKolektasView(page kolektas.PageResult, kelompok string) (string, tgbotapi.InlineKeyboardMarkup) {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var sb strings.Builder
	for i := range page.Items {
		sb.WriteString(formatKolektasItem(&page.Items[i]))
	}
	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if page.Page > 1 {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
			fmt.Sprintf("kolektas_%s_%d", kelompok, page.Page-1)))
	}
	first := (page.Page-1)*page.Size + 1
	last := (page.Page-1)*page.Size + int64(len(page.Items))
	row = append(row, tgbotapi.NewInlineKeyboardButtonData(
		fmt.Sprintf("%d - %d / %d", first, last, page.Total), "none"))
	if page.Page < totalPages {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData("Next ➡",
			fmt.Sprintf("kolektas_%s_%d", kelompok, page.Page+1)))
	}
	return sb.String(), tgbotapi.NewInlineKeyboardMarkup(row)
}

func formatKolektasItem(k *entity.KolekTas) string {
	addr := k.Alamat
	if len(addr) > 30 {
		addr = addr[:29] + "..."
	}
	return fmt.Sprintf(`
👤 *%s*
📝 Rek: `+"`%s`"+`
📍 Alamat: %s
💸 Tunggakan: %s
✨ Kelompok: %s
📱 No. HP: %s
📊 Kolek: %s
`,
		k.Nama, k.Rekening, addr, k.Nominal, k.Kelompok,
		utils.FormatPhoneNumber(k.NoHP), k.Kolek)
}

// BuildKolektasView dieksport untuk callback handler.
func BuildKolektasView(page kolektas.PageResult, kelompok string) (string, tgbotapi.InlineKeyboardMarkup) {
	return buildKolektasView(page, kelompok)
}
