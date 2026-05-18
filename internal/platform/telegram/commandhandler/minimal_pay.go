package commandhandler

import (
	"context"
	"fmt"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// MinimalPay /pabpr — daftar tagihan dengan minimal bayar tersisa.
//   - role AO         : filter accountOfficer = userCode
//   - role PIMP/ADMIN : filter branch         = userCode
type MinimalPay struct {
	Bills *bill.Service
	Users *users.Service
}

func (h *MinimalPay) Command() string { return "/pabpr" }
func (h *MinimalPay) Description() string {
	return "Daftar tagihan dengan minimal bayar tersisa"
}

func (h *MinimalPay) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		_, _ = b.SendText(chatID, "❌ *User tidak ditemukan*")
		return
	}
	page, ok := fetchMinimalPay(ctx, h.Bills, user, 0)
	if !ok || len(page.Items) == 0 {
		_, _ = b.SendText(chatID, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*")
		return
	}
	text, kb := buildMinimalPayView(page, user.UserCode, 0)
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
}

// fetchMinimalPay lookup berdasarkan role; ok=false untuk role tidak didukung.
func fetchMinimalPay(ctx context.Context, billsSvc *bill.Service, user *entity.User, page int64) (bill.PageResult[entity.Bills], bool) {
	switch user.Roles {
	case entity.RoleAO:
		p, err := billsSvc.FindMinimalPaymentByAO(ctx, user.UserCode, page, 5)
		return p, err == nil
	case entity.RolePIMP, entity.RoleAdmin:
		p, err := billsSvc.FindMinimalPaymentByBranch(ctx, user.UserCode, page, 5)
		return p, err == nil
	default:
		return bill.PageResult[entity.Bills]{}, false
	}
}

// buildMinimalPayView render header + entri + pagination.
// Data callback: "minimalpay_<userCode>_<page>".
func buildMinimalPayView(page bill.PageResult[entity.Bills], code string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var sb strings.Builder
	sb.WriteString("📋 *DAFTAR TAGIHAN MINIMAL*\n═══════════════════════════\n\n")
	for i := range page.Items {
		sb.WriteString(formatMinimalPayItem(&page.Items[i]))
	}
	sb.WriteString("⚠️ *Catatan Penting*:\n▢ _Tap SPK untuk menyalin_\n▢ _Pembayaran harus dilakukan sebelum jatuh bayar_\n")

	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if currentPage > 0 {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
			fmt.Sprintf("minimalpay_%s_%d", code, currentPage-1)))
	}
	first := currentPage*page.Size + 1
	last := currentPage*page.Size + int64(len(page.Items))
	row = append(row, tgbotapi.NewInlineKeyboardButtonData(
		fmt.Sprintf("%d - %d / %d", first, last, page.Total), "none"))
	if currentPage+1 < totalPages {
		row = append(row, tgbotapi.NewInlineKeyboardButtonData("Next ➡",
			fmt.Sprintf("minimalpay_%s_%d", code, currentPage+1)))
	}
	return sb.String(), tgbotapi.NewInlineKeyboardMarkup(row)
}

func formatMinimalPayItem(b *entity.Bills) string {
	return fmt.Sprintf(`🔑 *SPK*: `+"`%s`"+`
👤 *Nama*: *%s*
🏠 *Alamat*: %s

💳 *Minimal Pembayaran*
• Pokok: %s
• Bunga: %s

💰 *TOTAL*: %s
`,
		b.NoSpk, b.Name, b.Address,
		utils.FormatRupiah(b.MinPrincipal),
		utils.FormatRupiah(b.MinInterest),
		utils.FormatRupiah(b.MinPrincipal+b.MinInterest))
}

// BuildMinimalPayView untuk callback handler reuse.
func BuildMinimalPayView(page bill.PageResult[entity.Bills], code string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	return buildMinimalPayView(page, code, currentPage)
}

// FetchMinimalPay untuk callback handler reuse.
func FetchMinimalPay(ctx context.Context, billsSvc *bill.Service, user *entity.User, page int64) (bill.PageResult[entity.Bills], bool) {
	return fetchMinimalPay(ctx, billsSvc, user, page)
}
