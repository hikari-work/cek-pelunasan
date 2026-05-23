package slik

import (
	"fmt"
	"path"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// MaxContentChars batas karakter pesan SLIK sebelum daftar fasilitas dipotong.
// Telegram limit 4096; sisakan ruang untuk footer dan markdown overhead.
const MaxContentChars = 3800

var indMonthShort = [12]string{"Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"}

// PageData mewakili satu halaman hasil pencarian SLIK.
//   - ContentKey: full S3 path PDF (mis. "2026_05/pdf/SMG_budi.pdf")
//   - IDNumber:   16-digit NIK (atau "" kalau gagal extract)
//   - DTO:        parsed JSON SLIK (atau nil kalau file txt tidak ditemukan)
type PageData struct {
	ContentKey string
	IDNumber   string
	DTO        *JsonDto
}

// FormatPage bangun pesan Markdown untuk halaman ke-current dari total. Output
// dibatasi MaxContentChars karakter konten; sisanya dipotong dengan catatan.
func FormatPage(p PageData, current, total int) string {
	if p.DTO == nil {
		return formatNoData(p, current, total)
	}
	return formatFull(p, current, total)
}

func formatFull(p PageData, current, total int) string {
	dto := p.DTO
	var b strings.Builder

	nama := ""
	if len(dto.Individual.DataPokokDebitur) > 0 {
		nama = dto.Individual.DataPokokDebitur[0].NamaDebitur
	}
	fmt.Fprintf(&b, "*📊 HASIL SLIK — %s*\n", mdEscape(orDash(nama)))
	b.WriteString("━━━━━━━━━━━━━━━━━━━━━\n")

	hdr := dto.Header
	if hdr.KodeReferensiPengguna != "" || hdr.TanggalPermintaan != "" {
		fmt.Fprintf(&b, "📋 Ref: `%s`\n", orDash(hdr.KodeReferensiPengguna))
		fmt.Fprintf(&b, "📅 Tanggal: %s\n", formatDateTime(hdr.TanggalPermintaan))
	}

	if len(dto.Individual.DataPokokDebitur) > 0 {
		deb := dto.Individual.DataPokokDebitur[0]
		b.WriteString("\n👤 *DATA DEBITUR*\n")
		fmt.Fprintf(&b, "🪪 KTP: `%s`\n", orDash(deb.NoIdentitas))
		fmt.Fprintf(&b, "📍 Alamat: %s\n", mdEscape(orDash(deb.Alamat)))
		if deb.TempatLahir != "" || deb.TanggalLahir != "" {
			fmt.Fprintf(&b, "🎂 Lahir: %s, %s\n", mdEscape(orDash(deb.TempatLahir)), formatDate(deb.TanggalLahir))
		}
		fmt.Fprintf(&b, "💼 Pekerjaan: %s\n", mdEscape(orDash(deb.PekerjaanKet)))
	}

	if rf := dto.Individual.RingkasanFasilitas; rf != nil {
		b.WriteString("\n📈 *RINGKASAN FASILITAS*\n")
		fmt.Fprintf(&b, "Kol. Terburuk : *%s*\n", mdEscape(orDash(rf.KualitasTerburuk)))
		fmt.Fprintf(&b, "Bulan Data    : %s\n", formatYearMonth(rf.KualitasBulanDataTerburuk))
		fmt.Fprintf(&b, "Plafon Total  : %s\n", formatRupiahStr(rf.PlafonEfektifTotal))
		fmt.Fprintf(&b, "Baki Debet    : %s\n", formatRupiahStr(rf.BakiDebetTotal))
	}

	if dto.Individual.Fasilitas != nil && len(dto.Individual.Fasilitas.KreditPembiayan) > 0 {
		list := dto.Individual.Fasilitas.KreditPembiayan
		fmt.Fprintf(&b, "\n💳 *FASILITAS KREDIT (%d)*\n", len(list))
		shown := 0
		for i, k := range list {
			fas := buildFasilitasText(i+1, &k)
			// 200 chars buffer untuk footer.
			if b.Len()+len(fas)+200 > MaxContentChars {
				rem := len(list) - shown
				fmt.Fprintf(&b, "_(+%d fasilitas lainnya — gunakan /slik {ktp} untuk detail)_\n", rem)
				break
			}
			b.WriteString(fas)
			b.WriteString("\n")
			shown++
		}
	}

	writeFooter(&b, p, current, total)
	return b.String()
}

func formatNoData(p PageData, current, total int) string {
	var b strings.Builder
	fmt.Fprintf(&b, "*📊 HASIL SLIK — %s*\n", mdEscape(extractDisplayName(p.ContentKey)))
	b.WriteString("━━━━━━━━━━━━━━━━━━━━━\n")
	if p.IDNumber != "" {
		fmt.Fprintf(&b, "🪪 No KTP: `%s`\n", p.IDNumber)
	} else {
		b.WriteString("🪪 No KTP: _Tidak ditemukan_\n")
	}
	b.WriteString("\n_Data identitas tidak tersedia_\n")
	writeFooter(&b, p, current, total)
	return b.String()
}

func writeFooter(b *strings.Builder, p PageData, current, total int) {
	if p.IDNumber != "" {
		fmt.Fprintf(b, "\n🎯 `/slik %s`\n", p.IDNumber)
	}
	if p.ContentKey != "" {
		fmt.Fprintf(b, "📥 `/doc %s`\n", path.Base(p.ContentKey))
	}
	fmt.Fprintf(b, "_📄 Halaman %d dari %d_", current+1, total)
}

func buildFasilitasText(index int, k *KreditPembiayaan) string {
	var b strings.Builder
	fmt.Fprintf(&b, "%d. %s\n", index, mdEscape(orDash(k.LjkKet)))
	if k.CabangKet != "" {
		fmt.Fprintf(&b, "Cabang    : %s\n", mdEscape(k.CabangKet))
	}
	fmt.Fprintf(&b, "Plafon    : %s | Bakidebet: %s\n",
		formatRupiahStr(k.PlafonAwal), formatRupiahStr(k.BakiDebet))
	fmt.Fprintf(&b, "Kondisi   : %s | Kol: %s\n",
		mdEscape(orDash(k.KondisiKet)), mdEscape(orDash(k.KualitasKet)))
	if k.JenisPenggunaanKet != "" {
		fmt.Fprintf(&b, "Penggunaan: %s\n", mdEscape(k.JenisPenggunaanKet))
	}
	if k.TanggalAkadAwal != "" || k.TanggalJatuhTempo != "" {
		fmt.Fprintf(&b, "Jangka    : %s → %s\n",
			formatDate(k.TanggalAkadAwal), formatDate(k.TanggalJatuhTempo))
	}
	worstKol := findWorstKol(k.TahunBulan)
	maxHt := findMaxHt(k.TahunBulan)
	kolPeriod := findKolPeriodLabel(k.TahunBulan)
	kolLabel := "Kol Terburuk"
	if kolPeriod != "" {
		kolLabel = "Kol Terburuk s.d. " + kolPeriod
	}
	if worstKol != "" {
		fmt.Fprintf(&b, "%s: %s\n", kolLabel, worstKol)
	}
	if maxHt != "" && maxHt != "0" {
		fmt.Fprintf(&b, "Max Hari Tunggakan: %s hari\n", maxHt)
	}
	return b.String()
}

func findWorstKol(tb map[string]string) string {
	var maxVal int
	var found string
	for k, v := range tb {
		if !strings.HasSuffix(k, "Kol") || strings.TrimSpace(v) == "" {
			continue
		}
		n, err := strconv.Atoi(strings.TrimSpace(v))
		if err != nil {
			continue
		}
		if n > maxVal {
			maxVal = n
			found = v
		}
	}
	return found
}

func findMaxHt(tb map[string]string) string {
	var maxVal int
	var found string
	for k, v := range tb {
		if !strings.HasSuffix(k, "Ht") || strings.TrimSpace(v) == "" {
			continue
		}
		n, err := strconv.Atoi(strings.TrimSpace(v))
		if err != nil {
			continue
		}
		if n > maxVal {
			maxVal = n
			found = v
		}
	}
	return found
}

// findKolPeriodLabel ambil tahunBulanNN dengan NN terbesar yang punya pasangan
// Kol non-empty, lalu format jadi "Mar 2026".
func findKolPeriodLabel(tb map[string]string) string {
	type entry struct {
		nn     int
		period string
	}
	candidates := make([]entry, 0, len(tb))
	for k, v := range tb {
		if !strings.HasPrefix(k, "tahunBulan") || !strings.HasSuffix(k, "Kol") {
			continue
		}
		if strings.TrimSpace(v) == "" {
			continue
		}
		mid := strings.TrimSuffix(strings.TrimPrefix(k, "tahunBulan"), "Kol")
		nn, err := strconv.Atoi(mid)
		if err != nil || nn <= 0 {
			continue
		}
		periodKey := fmt.Sprintf("tahunBulan%02d", nn)
		period, ok := tb[periodKey]
		if !ok || len(period) < 6 {
			continue
		}
		candidates = append(candidates, entry{nn, period})
	}
	if len(candidates) == 0 {
		return ""
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].nn > candidates[j].nn })
	period := candidates[0].period
	year, errY := strconv.Atoi(period[:4])
	mon, errM := strconv.Atoi(period[4:6])
	if errY != nil || errM != nil || mon < 1 || mon > 12 {
		return ""
	}
	return fmt.Sprintf("%s %d", indMonthShort[mon-1], year)
}

func formatDateTime(raw string) string {
	if len(raw) < 14 {
		return orDash(raw)
	}
	t, err := time.Parse("20060102150405", raw[:14])
	if err != nil {
		return raw
	}
	return t.Format("02/01/2006 15:04")
}

func formatDate(raw string) string {
	if len(raw) < 8 {
		return orDash(raw)
	}
	t, err := time.Parse("20060102", raw[:8])
	if err != nil {
		return raw
	}
	return t.Format("02/01/2006")
}

func formatYearMonth(raw string) string {
	if len(raw) < 6 {
		return orDash(raw)
	}
	return raw[4:6] + "/" + raw[:4]
}

func formatRupiahStr(raw string) string {
	if strings.TrimSpace(raw) == "" {
		return "Rp0"
	}
	n, err := strconv.ParseInt(strings.TrimSpace(raw), 10, 64)
	if err != nil {
		return raw
	}
	return utils.FormatRupiah(n)
}

func orDash(s string) string {
	if strings.TrimSpace(s) == "" {
		return "-"
	}
	return s
}

// extractDisplayName ambil basename - prefix AO + tanpa ext.
// "2026_05/pdf/SMG_budi.pdf" → "budi.pdf" (legacy hapus prefix AO sebelum '_').
// Setelah hapus ext: "budi". Tapi legacy hapus prefix AO bukan ext duluan; ikut.
func extractDisplayName(contentKey string) string {
	if contentKey == "" {
		return "-"
	}
	base := path.Base(contentKey)
	// Hapus extension.
	if dot := strings.LastIndex(base, "."); dot > 0 {
		base = base[:dot]
	}
	// Hapus prefix AO (sebelum underscore pertama).
	if u := strings.Index(base, "_"); u >= 0 && u < len(base)-1 {
		base = base[u+1:]
	}
	return base
}

// mdEscape escape karakter Markdown legacy (parse_mode = "Markdown" v1):
// _ * ` [. v1 lebih ringan dibanding MarkdownV2, cukup escape ini.
func mdEscape(s string) string {
	if s == "" {
		return s
	}
	r := strings.NewReplacer("_", "\\_", "*", "\\*", "`", "\\`", "[", "\\[")
	return r.Replace(s)
}
