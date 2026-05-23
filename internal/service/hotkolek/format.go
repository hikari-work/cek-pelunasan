package hotkolek

import (
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

// LocationBills satu lokasi kios + 3 kategori tagihan di dalamnya.
type LocationBills struct {
	Name     string
	Category []CategoryBills
}

// HasAnyData true kalau minimal satu kategori tidak kosong — lokasi
// tanpa data sama sekali tidak ditampilkan di pesan.
func (l LocationBills) HasAnyData() bool {
	for _, c := range l.Category {
		if len(c.Bills) > 0 {
			return true
		}
	}
	return false
}

// CategoryBills satu kategori dalam satu lokasi (mis. "Angsuran Pertama").
// Header kosong = tampilkan langsung daftar tanpa label kategori.
type CategoryBills struct {
	Header string
	Bills  []entity.Bills
}

// monthYearID nama bulan + tahun dalam bahasa Indonesia uppercase.
// Pakai tabel literal supaya tidak bergantung tzdata locale yang
// sering bermasalah di Alpine container.
var bulanID = [...]string{
	"JANUARI", "FEBRUARI", "MARET", "APRIL", "MEI", "JUNI",
	"JULI", "AGUSTUS", "SEPTEMBER", "OKTOBER", "NOVEMBER", "DESEMBER",
}

func monthYearID(t time.Time) string {
	idx := int(t.Month()) - 1
	if idx < 0 || idx >= len(bulanID) {
		return strings.ToUpper(t.Format("January 2006"))
	}
	return fmt.Sprintf("%s %d", bulanID[idx], t.Year())
}

// FormatHotKolekMessage rakit pesan rekap hot collection siap kirim ke WA.
//
// Layout per legacy HotKolekMessageGenerator:
//   - Header: "*HOT COLLECTION BULAN <BULAN TAHUN>*\n*TAGIHAN YG PENGARUH NPL*"
//   - Per lokasi: nama lokasi (skip kalau lokasi tidak punya data sama sekali),
//     lalu tiap kategori (skip kategori kosong; header kategori kalau ada).
//   - Tiap tagihan: "{idx:2d}. {NoSpk:13s} *{name}* {short_amount:>10}".
//   - Footer: instruksi standard untuk AO.
func FormatHotKolekMessage(locations []LocationBills, now time.Time) string {
	var b strings.Builder
	_, _ = fmt.Fprintf(&b, "*HOT COLLECTION BULAN %s*\n*TAGIHAN YG PENGARUH NPL*\n\n", monthYearID(now))

	for _, loc := range locations {
		b.WriteString("\n")
		if !loc.HasAnyData() {
			continue
		}
		_, _ = fmt.Fprintf(&b, "*%s* : \n", loc.Name)
		for _, cat := range loc.Category {
			b.WriteString("\n")
			if len(cat.Bills) == 0 {
				continue
			}
			if cat.Header != "" {
				b.WriteString(cat.Header + "\n")
			}
			appendBills(&b, cat.Bills)
		}
	}
	b.WriteString("\nBagi AO yg sudah mendapat tagihan dan tdk pengaruh NPL bisa langsung di hapus.\n")
	b.WriteString("Semoga NPL bulan ini bisa turun ,ttp semangat dan jaga kesehatan.")
	return b.String()
}

func appendBills(b *strings.Builder, bills []entity.Bills) {
	for i, bill := range bills {
		name := truncate(bill.Name, 20)
		// Format legacy: %2d. %-13s %-22s %10s — kolom name di-bold via "*name*",
		// jadi width 22 termasuk dua karakter bintang.
		nameField := "*" + name + "*"
		_, _ = fmt.Fprintf(b, "%2d. %-13s %-22s %10s\n", i+1, bill.NoSpk, nameField, FormatToShort(bill.DebitTray))
	}
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

// FormatToShort ringkas nominal ke "1.5 Jt" / "750rb" / "500".
// Hanya satu desimal (kalau bukan bulat). Negatif jarang muncul di
// konteks tagihan, tapi tetap dibungkus untuk keamanan format.
func FormatToShort(value int64) string {
	abs := value
	negative := abs < 0
	if negative {
		abs = -abs
	}
	var out string
	switch {
	case abs >= 1_000_000:
		out = trimDecimal(float64(abs)/1_000_000) + " Jt"
	case abs >= 1_000:
		out = trimDecimal(float64(abs)/1_000) + "rb"
	default:
		out = strconv.FormatInt(abs, 10)
	}
	if negative {
		return "-" + out
	}
	return out
}

func trimDecimal(v float64) string {
	if v == float64(int64(v)) {
		return strconv.FormatInt(int64(v), 10)
	}
	return strconv.FormatFloat(v, 'f', 1, 64)
}
