package hotkolek

import (
	"strings"
	"testing"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

func TestFormatToShort(t *testing.T) {
	cases := map[int64]string{
		0:           "0",
		500:         "500",
		1_000:       "1rb",
		1_500:       "1.5rb",
		750_000:     "750rb",
		999_999:     "1000.0rb", // 999.999 → %.1f rounding
		1_000_000:   "1 Jt",
		1_500_000:   "1.5 Jt",
		2_300_000:   "2.3 Jt",
		10_000_000:  "10 Jt",
		-2_000_000:  "-2 Jt",
	}
	for in, want := range cases {
		if got := FormatToShort(in); got != want {
			t.Errorf("FormatToShort(%d) = %q, want %q", in, got, want)
		}
	}
}

func TestMonthYearID(t *testing.T) {
	cases := []struct {
		in   time.Time
		want string
	}{
		{time.Date(2026, 1, 15, 0, 0, 0, 0, time.UTC), "JANUARI 2026"},
		{time.Date(2026, 5, 1, 0, 0, 0, 0, time.UTC), "MEI 2026"},
		{time.Date(2026, 12, 31, 0, 0, 0, 0, time.UTC), "DESEMBER 2026"},
	}
	for _, c := range cases {
		if got := monthYearID(c.in); got != c.want {
			t.Errorf("monthYearID(%v) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestTruncate(t *testing.T) {
	cases := []struct {
		in   string
		n    int
		want string
	}{
		{"BUDI", 20, "BUDI"},
		{"BUDI SANTOSO YANG SANGAT PANJANG", 20, "BUDI SANTOSO YANG SA"},
		{"", 5, ""},
	}
	for _, c := range cases {
		if got := truncate(c.in, c.n); got != c.want {
			t.Errorf("truncate(%q, %d) = %q, want %q", c.in, c.n, got, c.want)
		}
	}
}

func TestFormatHotKolekMessage_Empty(t *testing.T) {
	now := time.Date(2026, 5, 1, 0, 0, 0, 0, time.UTC)
	msg := FormatHotKolekMessage(nil, now)
	if !strings.Contains(msg, "HOT COLLECTION BULAN MEI 2026") {
		t.Errorf("missing header: %q", msg)
	}
	if !strings.Contains(msg, "tdk pengaruh NPL bisa langsung di hapus") {
		t.Errorf("missing footer: %q", msg)
	}
}

func TestFormatHotKolekMessage_LocationWithoutDataSkipped(t *testing.T) {
	now := time.Date(2026, 5, 1, 0, 0, 0, 0, time.UTC)
	locs := []LocationBills{
		{
			Name: "Kosong",
			Category: []CategoryBills{
				{Header: "", Bills: nil},
				{Header: "Angsuran Pertama", Bills: nil},
			},
		},
	}
	msg := FormatHotKolekMessage(locs, now)
	if strings.Contains(msg, "*Kosong*") {
		t.Errorf("lokasi tanpa data seharusnya tidak ditampilkan: %s", msg)
	}
}

func TestFormatHotKolekMessage_BillsLineFormat(t *testing.T) {
	now := time.Date(2026, 5, 1, 0, 0, 0, 0, time.UTC)
	locs := []LocationBills{
		{
			Name: "Kaligondang",
			Category: []CategoryBills{
				{Header: "", Bills: []entity.Bills{
					{NoSpk: "010600001234", Name: "BUDI", DebitTray: 2_500_000},
				}},
			},
		},
	}
	msg := FormatHotKolekMessage(locs, now)
	mustContain := []string{
		"*Kaligondang* :",
		"010600001234",
		"*BUDI*",
		"2.5 Jt",
	}
	for _, w := range mustContain {
		if !strings.Contains(msg, w) {
			t.Errorf("missing %q in:\n%s", w, msg)
		}
	}
}

func TestFormatHotKolekMessage_CategoryHeader(t *testing.T) {
	now := time.Date(2026, 5, 1, 0, 0, 0, 0, time.UTC)
	locs := []LocationBills{
		{
			Name: "Kalikajar",
			Category: []CategoryBills{
				{Header: "", Bills: []entity.Bills{}}, // skip kategori kosong
				{Header: "Angsuran Pertama", Bills: []entity.Bills{
					{NoSpk: "010600009999", Name: "DEDI", DebitTray: 100_000},
				}},
				{Header: "Jatuh tempo", Bills: []entity.Bills{
					{NoSpk: "010600008888", Name: "EKO", DebitTray: 50_000},
				}},
			},
		},
	}
	msg := FormatHotKolekMessage(locs, now)
	if !strings.Contains(msg, "Angsuran Pertama") {
		t.Errorf("missing 'Angsuran Pertama' header: %s", msg)
	}
	if !strings.Contains(msg, "Jatuh tempo") {
		t.Errorf("missing 'Jatuh tempo' header: %s", msg)
	}
	if !strings.Contains(msg, "100rb") {
		t.Errorf("missing 100rb amount: %s", msg)
	}
}
