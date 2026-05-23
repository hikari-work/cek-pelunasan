package savings

import (
	"context"
	"fmt"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

const dataTypeSaving = "SAVING"

// FormatDetail menghasilkan blok detail satu rekening tabungan (Markdown).
// Saldo efektif = (Buku + Transaksi) - Minimum - Blokir.
func FormatDetail(s *entity.Savings) string {
	book := s.Balance + s.Transaction
	effective := book - s.MinimumBalance - s.BlockingBalance
	var b strings.Builder
	_, _ = fmt.Fprintf(&b, "👤 *%s*\n", s.Name)
	_, _ = fmt.Fprintf(&b, "No. Rek: `%s`\n", s.TabID)
	_, _ = fmt.Fprintf(&b, "Alamat: %s\n\n", s.Address)
	b.WriteString("💰 Saldo:\n")
	_, _ = fmt.Fprintf(&b, "• Buku: %s\n", utils.FormatRupiah(book))
	_, _ = fmt.Fprintf(&b, "• Min: %s\n", utils.FormatRupiah(s.MinimumBalance))
	_, _ = fmt.Fprintf(&b, "• Block: %s\n", utils.FormatRupiah(s.BlockingBalance))
	_, _ = fmt.Fprintf(&b, "• Efektif: `%s`\n\n", utils.FormatRupiah(effective))
	return b.String()
}

// FormatPage padanan SavingsUtils.buildMessage. Header halaman + semua item +
// peringatan kalau data SAVING bukan dari hari ini.
func (s *Service) FormatPage(ctx context.Context, page PageResult, durationMS int64) string {
	totalPages := int64(0)
	if page.Size > 0 {
		totalPages = (page.Total + page.Size - 1) / page.Size
		if totalPages == 0 {
			totalPages = 1
		}
	}
	var b strings.Builder
	_, _ = fmt.Fprintf(&b, "📊 *INFORMASI TABUNGAN*\nHalaman %d dari %d\n\n", page.Page+1, totalPages)
	for i := range page.Items {
		b.WriteString(FormatDetail(&page.Items[i]))
	}
	_, _ = fmt.Fprintf(&b, "⏱️ Waktu: %dms", durationMS)
	b.WriteString(s.updates.TelegramWarning(ctx, dataTypeSaving))
	return b.String()
}

// FormatCanvas padanan CanvasingUtils.canvasingTab — versi ringkas untuk
// kegiatan kanvasing lapangan.
func FormatCanvas(s *entity.Savings) string {
	return fmt.Sprintf(`👤 *%s*
📊 *Data Nasabah*
• 🆔 CIF: `+"`%s`"+`
• 📍 Alamat: %s
• 💵 Saldo: %s

`,
		s.Name, s.CIF, s.Address, utils.FormatRupiah(s.Balance))
}
