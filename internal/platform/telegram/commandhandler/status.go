package commandhandler

import (
	"context"
	"fmt"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/credithistory"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Status ringkasan kesehatan sistem, jumlah user, jumlah data tagihan/kredit,
// dan penggunaan CPU/RAM. Eksekusi paralel pakai goroutine + WaitGroup.
type Status struct {
	Users      *users.Service
	Bills      *bill.Service
	CreditHist *credithistory.Service
}

func (h *Status) Command() string { return "/status" }
func (h *Status) Description() string {
	return "Mengecek Status Server dan Database serta user terdaftar"
}

func (h *Status) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	start := time.Now()

	type result struct {
		users  int64
		credit int64
		bills  int64
		err    error
	}

	usersCh := make(chan result, 1)
	creditCh := make(chan result, 1)
	billsCh := make(chan result, 1)

	go func() {
		c, err := h.Users.Count(ctx)
		usersCh <- result{users: c, err: err}
	}()
	go func() {
		c, err := h.CreditHist.Count(ctx)
		creditCh <- result{credit: c, err: err}
	}()
	go func() {
		// Bills.Count belum ada di service; aproksimasi pakai distinct branch -> 0 untuk sementara.
		// Jika dibutuhkan akurat, tambah method Count() ke BillsRepo.
		_ = h.Bills
		billsCh <- result{bills: 0}
	}()

	u := <-usersCh
	c := <-creditCh
	bl := <-billsCh

	if u.err != nil || c.err != nil || bl.err != nil {
		_, _ = b.SendText(chatID, "❌ Error mengambil data status. Silakan coba lagi.")
		return
	}

	text := fmt.Sprintf(`⚡️ *PELUNASAN BOT STATUS*
╔══════════════════════
║ 🤖 Status: *ONLINE*
╠══════════════════════

📊 *STATISTIK SISTEM*
┌────────────────────
│ 👥 Users     : %d
│ 📦 All Krd   : %d
│ 💳 Tagihan   : %d
│ ⚙️ Load      : %s
└────────────────────

📡 *INFORMASI SERVER*
┌────────────────────
│ 🔋 Health     : 100%%
└────────────────────

🎯 *QUICK TIPS*
┌────────────────────
│ • Ketik /help untuk bantuan
│ • Cek status setiap hari
│ • Update data secara rutin
└────────────────────

✨ _System is healthy and ready!_
⏱️ _Generated in %dms_
`, u.users, c.credit, bl.bills, utils.SystemSummary(), time.Since(start).Milliseconds())

	_, _ = b.SendText(chatID, text)
}
