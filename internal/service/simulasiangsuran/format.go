package simulasiangsuran

import (
	"fmt"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// FormatTelegram menghasilkan pesan Markdown dari hasil simulasi.
// Padanan SimulasiAngsuranFormatter.format.
func FormatTelegram(result Result, b *entity.Bills) string {
	var sb strings.Builder
	sb.WriteString("📊 *Simulasi Angsuran*\n")
	sb.WriteString(fmt.Sprintf("SPK: `%s` — %s\n", b.NoSpk, b.Name))
	sb.WriteString(fmt.Sprintf("Hari Keterlambatan: *%s hari*\n", b.DayLate))
	sb.WriteString("\n━━━━━━━━━━━━━━━━━━━━\n\n")

	for _, sk := range result.Skenarios {
		sb.WriteString(formatSkenario(sk))
		sb.WriteString("\n")
	}

	sb.WriteString("━━━━━━━━━━━━━━━━━━━━\n")
	sb.WriteString(fmt.Sprintf("✅ *Rekomendasi: Skenario %s*\n", result.RekomendasiSkenario))
	sb.WriteString(fmt.Sprintf("Total Bayar Minimum: *%s*", utils.FormatRupiah(result.TotalBayarMinimum)))
	return sb.String()
}

func formatSkenario(sk Skenario) string {
	var sb strings.Builder
	switch sk.Kode {
	case "A":
		sb.WriteString("🅰️ *Skenario A — " + sk.Nama + "*\n")
	case "B":
		sb.WriteString("🅱️ *Skenario B — " + sk.Nama + "*\n")
	default:
		sb.WriteString("🆚 *Skenario C — " + sk.Nama + "*\n")
	}
	sb.WriteString(fmt.Sprintf("💰 Total: *%s*\n", utils.FormatRupiah(sk.TotalBayar)))
	multi := len(sk.Tahap) > 1
	for _, t := range sk.Tahap {
		sb.WriteString(formatTahap(t, multi))
	}
	sb.WriteString(fmt.Sprintf("_%s_\n", sk.Keterangan))
	return sb.String()
}

func formatTahap(t TahapPembayaran, multi bool) string {
	var sb strings.Builder
	tgl := formatTanggalID(t.Tanggal)
	if multi {
		sb.WriteString(fmt.Sprintf("  📅 *%s*\n", tgl))
		sb.WriteString(fmt.Sprintf("  Bayar: %s\n", utils.FormatRupiah(t.JumlahBayar)))
	} else {
		sb.WriteString(fmt.Sprintf("📅 Tanggal: %s\n", tgl))
	}
	if t.AlokasiBunga > 0 {
		sb.WriteString(fmt.Sprintf("  • Bunga: %s\n", utils.FormatRupiah(t.AlokasiBunga)))
	}
	if t.AlokasiPokok > 0 {
		sb.WriteString(fmt.Sprintf("  • Pokok: %s\n", utils.FormatRupiah(t.AlokasiPokok)))
	}
	return sb.String()
}

// bulanID nama bulan Bahasa Indonesia (Locale id-ID di Java).
var bulanID = [...]string{
	"Januari", "Februari", "Maret", "April", "Mei", "Juni",
	"Juli", "Agustus", "September", "Oktober", "November", "Desember",
}

func formatTanggalID(t time.Time) string {
	t = t.In(jakartaTZ)
	return fmt.Sprintf("%d %s %d", t.Day(), bulanID[int(t.Month())-1], t.Year())
}
