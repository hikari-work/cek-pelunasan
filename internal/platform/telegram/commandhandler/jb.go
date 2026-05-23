package commandhandler

import (
	"context"
	"fmt"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// JatuhBayar /jb — tampilkan tagihan dengan payDown = tanggal hari ini.
//   - role AO  : filter accountOfficer = userCode
//   - role PIMP: filter branch         = userCode
//
// User selain itu tidak menampilkan apa-apa (sesuai legacy).
type JatuhBayar struct {
	Bills *bill.Service
	Users *users.Service
}

func (h *JatuhBayar) Command() string { return "/jb" }
func (h *JatuhBayar) Description() string {
	return "📅 Cek tagihan jatuh tempo hari ini"
}

func (h *JatuhBayar) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		_, _ = b.SendText(chatID, "❌ *User tidak ditemukan*")
		return
	}
	today := utils.DayOfMonth(time.Now().In(logsvc.JakartaTZ))
	page, ok := fetchJB(ctx, h.Bills, user, today, 0)
	if !ok {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}
	if len(page.Items) == 0 {
		_, _ = b.SendText(chatID, "❌ *Data tidak ditemukan*")
		return
	}
	text, kb := buildJBView(page, user.UserCode, 0)
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
}

// fetchJB lookup sesuai role; mengembalikan page kosong + ok=false untuk role yang
// tidak didukung.
func fetchJB(ctx context.Context, billsSvc *bill.Service, user *entity.User, today string, page int64) (bill.PageResult[entity.Bills], bool) {
	switch user.Roles {
	case entity.RoleAO, entity.RoleAdmin:
		p, err := billsSvc.FindByAOAndPayDown(ctx, user.UserCode, today, page, 5)
		return p, err == nil
	case entity.RolePIMP:
		p, err := billsSvc.FindByBranchAndPayDown(ctx, user.UserCode, today, page, 5)
		return p, err == nil
	default:
		return bill.PageResult[entity.Bills]{}, false
	}
}

// buildJBView render header + entri ringkas + pagination.
// Data callback: "tagihNext_<userCode>_<page>".
func buildJBView(page bill.PageResult[entity.Bills], code string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var sb strings.Builder
	_, _ = fmt.Fprintf(&sb, "Halaman %d dari %d\n📋 *Daftar Tagihan Jatuh Tempo Hari Ini:*\n\n",
		currentPage+1, totalPages)
	for i := range page.Items {
		sb.WriteString(formatJBItem(&page.Items[i]))
	}

	row := make([]tgbotapi.InlineKeyboardButton, 0, 3)
	if currentPage > 0 {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("⬅ Prev",
				fmt.Sprintf("tagihNext_%s_%d", code, currentPage-1)))
	}
	first := currentPage*page.Size + 1
	last := currentPage*page.Size + int64(len(page.Items))
	row = append(row,
		tgbotapi.NewInlineKeyboardButtonData(
			fmt.Sprintf("%d - %d / %d", first, last, page.Total), "none"))
	if currentPage+1 < totalPages {
		row = append(row,
			tgbotapi.NewInlineKeyboardButtonData("Next ➡",
				fmt.Sprintf("tagihNext_%s_%d", code, currentPage+1)))
	}
	return sb.String(), tgbotapi.NewInlineKeyboardMarkup(row)
}

func formatJBItem(b *entity.Bills) string {
	addr := b.Address
	if len(addr) > 30 {
		addr = addr[:27] + "..."
	}
	pay := b.PayDown
	if strings.TrimSpace(pay) == "" {
		pay = "Tidak tersedia"
	}
	return fmt.Sprintf(`👤 *%s*
┌──────────────────────
│ 📎 *INFORMASI KREDIT*
│ ├─ 🔖 SPK      : `+"`%s`"+`
│ ├─ 📍 Alamat   : %s
│ └─ 📅 Jth Tempo: %s
│
│ 💰 *RINCIAN*
│ ├─ 💸 Tagihan  : %s
│ └─ 👨‍💼 AO       : %s
└──────────────────────

ℹ️ _Tap SPK untuk menyalin_
`,
		b.Name, b.NoSpk, addr, pay,
		utils.FormatRupiah(b.FullPayment), b.AccountOfficer)
}

// BuildJBView dieksport untuk callback handler reuse.
func BuildJBView(page bill.PageResult[entity.Bills], code string, currentPage int64) (string, tgbotapi.InlineKeyboardMarkup) {
	return buildJBView(page, code, currentPage)
}

// FetchJB dieksport untuk callback handler.
func FetchJB(ctx context.Context, billsSvc *bill.Service, user *entity.User, today string, page int64) (bill.PageResult[entity.Bills], bool) {
	return fetchJB(ctx, billsSvc, user, today, page)
}
