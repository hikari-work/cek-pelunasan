package bill

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

const dataTypeTagihan = "TAGIHAN"

// DetailMarkdown memformat satu Bills ke pesan Telegram (Markdown) yang lengkap.
// Padanan TagihanUtils.detailBills.
func (s *Service) DetailMarkdown(ctx context.Context, b *entity.Bills) string {
	totalTagihan := calculateTotalPayment(b)
	body := fmt.Sprintf(`📄 *Detail Kredit*

👤 *Nasabah*
• Nama: *%s*
• No SPK: `+"`%s`"+`
• Alamat: %s
• Produk: %s

💳 *Pinjaman*
• Plafond: %s
• Baki Debet: %s
• Realisasi: %s
• Jatuh Tempo: %s

💹 *Angsuran*
• Bunga: %s
• Pokok: %s
• Total: %s

⚠️ *Tunggakan*
• Bunga: %s
• Pokok: %s

📊 *Status*
• Kolektibilitas: %s

💸 *Tagihan*
• Total: %s
• Min. Pokok: %s
• Min. Bunga: %s

👨‍💼 *AO*: %s
`,
		b.Name,
		b.NoSpk,
		b.Address,
		b.Product,
		utils.FormatRupiah(b.Plafond),
		utils.FormatRupiah(b.DebitTray),
		b.Realization,
		b.DueDate,
		utils.FormatRupiah(b.Interest),
		utils.FormatRupiah(b.Principal),
		utils.FormatRupiah(b.Installment),
		utils.FormatRupiah(b.LastInterest),
		utils.FormatRupiah(b.LastPrincipal),
		b.CollectStatus,
		utils.FormatRupiah(totalTagihan),
		utils.FormatRupiah(b.MinPrincipal),
		utils.FormatRupiah(b.MinInterest),
		b.AccountOfficer,
	)
	return body + s.updates.TelegramWarning(ctx, dataTypeTagihan)
}

// CompactMarkdown padanan TagihanUtils.billsCompact — tampilan ringkas untuk daftar.
func (s *Service) CompactMarkdown(ctx context.Context, b *entity.Bills) string {
	now := time.Now().In(logsvc.JakartaTZ).Format("02/01/2006 15:04:05")
	body := fmt.Sprintf(`🏦 *INFORMASI NASABAH*
━━━━━━━━━━━━━━━━━━━

👤 *%s*
📄 ID SPK: `+"`%s`"+`
📍 Alamat: %s

📅 *Tempo*
• Jatuh Tempo: %s

💰 *Tagihan*
• Total: %s

👨‍💼 *Account Officer*
• AO: %s

⏱️ _Generated: %s_
`,
		b.Name,
		b.NoSpk,
		b.Address,
		b.PayDown,
		utils.FormatRupiah(b.FullPayment),
		b.AccountOfficer,
		now,
	)
	return body + s.updates.TelegramWarning(ctx, dataTypeTagihan)
}

// calculateTotalPayment: total bayar tergantung tanggal realisasi vs hari ini (WIB).
//   - realisasi == hari ini  → fullPayment
//   - realisasi < hari ini   → lastInterest + lastPrincipal
//   - realisasi > hari ini   → installment
func calculateTotalPayment(b *entity.Bills) int64 {
	realisasi := strings.TrimSpace(b.Realization)
	if realisasi == "" {
		return b.Installment
	}
	tgl, err := time.ParseInLocation("02-01-2006", realisasi, logsvc.JakartaTZ)
	if err != nil {
		return b.Installment
	}
	now := time.Now().In(logsvc.JakartaTZ)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, logsvc.JakartaTZ)
	switch {
	case tgl.Equal(today):
		return b.FullPayment
	case tgl.Before(today):
		return b.LastInterest + b.LastPrincipal
	default:
		return b.Installment
	}
}
