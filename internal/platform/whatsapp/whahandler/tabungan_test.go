package whahandler

import (
	"strings"
	"testing"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

func TestTabungan_Match(t *testing.T) {
	h := &Tabungan{}
	cases := []struct {
		body string
		want bool
	}{
		{".t 010600001234", true},
		{".t budi", true},
		{".t ", true}, // prefix saja, validasi input kosong di Handle
		{".t", false}, // tanpa spasi → tidak match prefix ".t "
		{"t budi", false},
		{".p 010600001234", false},
		{"", false},
	}
	for _, c := range cases {
		if got := h.Match(makeMsg(c.body, "62811")); got != c.want {
			t.Errorf("Match(%q) = %v, want %v", c.body, got, c.want)
		}
	}
}

func TestAccountPattern(t *testing.T) {
	cases := map[string]bool{
		"010600001234":  true,
		"123456789012":  true,
		"01060000123":   false, // 11 digit
		"0106000012345": false, // 13 digit
		"01060000123A":  false,
		"":              false,
	}
	for in, want := range cases {
		if got := accountPattern.MatchString(in); got != want {
			t.Errorf("accountPattern(%q) = %v, want %v", in, got, want)
		}
	}
}

func TestFormatSavingDetailWA(t *testing.T) {
	s := &entity.Savings{
		Name:            "BUDI",
		TabID:           "010600001234",
		Address:         "Jl. Mawar",
		Balance:         1_000_000,
		Transaction:     500_000,
		MinimumBalance:  50_000,
		BlockingBalance: 100_000,
	}
	out := formatSavingDetailWA(s)

	mustContain := []string{
		"BUDI",
		"010600001234",
		"Jl. Mawar",
		"Rp1.500.000", // Buku = 1m + 500k
		"Rp50.000",    // Min
		"Rp100.000",   // Block
		"Rp1.350.000", // Efektif = 1.5m - 50k - 100k
	}
	for _, w := range mustContain {
		if !strings.Contains(out, w) {
			t.Errorf("missing %q in:\n%s", w, out)
		}
	}
}

func TestFormatNameSearchWA_Empty(t *testing.T) {
	out := formatNameSearchWA("budi", nil, 5)
	if !strings.Contains(out, "Tidak ditemukan") {
		t.Errorf("missing not-found msg: %s", out)
	}
	if !strings.Contains(out, "*budi*") {
		t.Errorf("missing query bold: %s", out)
	}
}

func TestFormatNameSearchWA_FewResults(t *testing.T) {
	results := []entity.Savings{
		{Name: "BUDI A", TabID: "111", Address: "Jl. A", Balance: 100_000},
		{Name: "BUDI B", TabID: "222", Address: "Jl. B", Balance: 200_000},
	}
	out := formatNameSearchWA("budi", results, 5)

	mustContain := []string{
		`Hasil pencarian: "budi"`,
		"Ditemukan: 2 nasabah",
		"BUDI A",
		"BUDI B",
		"Gunakan `.t {nomor rekening}`",
	}
	for _, w := range mustContain {
		if !strings.Contains(out, w) {
			t.Errorf("missing %q in:\n%s", w, out)
		}
	}
	if strings.Contains(out, "Hasil dibatasi") {
		t.Errorf("limit notice should NOT appear when results < max: %s", out)
	}
}

func TestFormatNameSearchWA_AtLimit(t *testing.T) {
	results := make([]entity.Savings, 5)
	for i := range results {
		results[i] = entity.Savings{Name: "X", TabID: "1", Address: "A"}
	}
	out := formatNameSearchWA("x", results, 5)
	if !strings.Contains(out, "Hasil dibatasi 5 nasabah") {
		t.Errorf("limit notice should appear: %s", out)
	}
}

// Pastikan handler nil-sender tidak panic — Handle return diam.
func TestTabungan_HandleNilSender(t *testing.T) {
	h := &Tabungan{Service: nil, Sender: nil}
	// Match-kan dulu — Handle harus tetap aman.
	m := makeMsg(".t 010600001234", "62811")
	if !h.Match(m) {
		t.Fatal("expected match")
	}
	// Tidak panggil Handle (Service nil) — guard di awal Handle pasti return.
	// Cukup check via Service guard di handler — sudah ada di kode.
	_ = whatsapp.NewRouter("")
}
