package pelunasan

import (
	"strings"
	"testing"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

// today helper untuk inject tanggal "sekarang" yang stabil di test.
func today(y int, m time.Month, d int) time.Time {
	return time.Date(y, m, d, 12, 0, 0, 0, jakartaTZ)
}

func baseBills() *entity.Bills {
	return &entity.Bills{
		NoSpk:            "010600001234",
		Name:             "BUDI",
		Address:          "Jl. Mawar No. 1",
		Product:          "KMG-LM",
		Realization:      "2024-01-15",
		DueDate:          "2026-01-15",
		Plafond:          12_000_000,
		DebitTray:        5_000_000,
		Interest:         100_000,
		LastInterest:     200_000,
		Titipan:          50_000,
		FixedInterest:    150_000,
		PenaltyInterest:  10_000,
		PenaltyPrincipal: 5_000,
	}
}

// FlatMurni jangka panjang (>12 bulan), masih dalam 12 bulan pertama:
// monthsPassed=4, monthsLeft=20 → expect cap MaxLongTerm=6.
func TestCalculate_FlatMurni_LongTerm_EarlyMonths(t *testing.T) {
	b := baseBills()

	res, err := calculateAt(b, today(2024, 5, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 6 {
		t.Errorf("MultiplierPenalty = %d, want 6", res.MultiplierPenalty)
	}
	if res.Penalty != 6*150_000 {
		t.Errorf("Penalty = %d, want %d", res.Penalty, 6*150_000)
	}
	if res.Denda != 15_000 {
		t.Errorf("Denda = %d, want 15000", res.Denda)
	}
}

// FlatMurni jangka panjang, sudah lewat 12 bulan: cap turun ke MaxShortTerm=3.
func TestCalculate_FlatMurni_LongTerm_AfterYear(t *testing.T) {
	b := baseBills()

	// realisasi Jan 2024, today Mar 2025 → monthsPassed=14, monthsLeft=10.
	res, err := calculateAt(b, today(2025, 3, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 3 {
		t.Errorf("MultiplierPenalty = %d, want 3", res.MultiplierPenalty)
	}
}

// FlatMurni: jatuh tempo bulan dan tahun yang sama dengan today → multiplier 0.
func TestCalculate_FlatMurni_DueThisMonth(t *testing.T) {
	b := baseBills()
	res, err := calculateAt(b, today(2026, 1, 5))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 0 {
		t.Errorf("MultiplierPenalty = %d, want 0 (due this month)", res.MultiplierPenalty)
	}
	if res.Penalty != 0 {
		t.Errorf("Penalty = %d, want 0", res.Penalty)
	}
}

// FlatMurni jangka pendek (<=12 bulan), monthsPassed > 6 → cap min=1.
func TestCalculate_FlatMurni_ShortTerm_LateStage(t *testing.T) {
	b := baseBills()
	b.Realization = "2024-01-15"
	b.DueDate = "2024-12-15" // total 11 bulan

	// today Sep 2024 → monthsPassed=8, monthsLeft=3, threshold lewat → cap 1.
	res, err := calculateAt(b, today(2024, 9, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 1 {
		t.Errorf("MultiplierPenalty = %d, want 1", res.MultiplierPenalty)
	}
}

// FlatMurni jangka pendek, monthsPassed <= 6 → cap MaxShortTerm=3.
func TestCalculate_FlatMurni_ShortTerm_EarlyStage(t *testing.T) {
	b := baseBills()
	b.Realization = "2024-01-15"
	b.DueDate = "2024-12-15"

	// today Apr 2024 → monthsPassed=3, monthsLeft=8 → cap 3.
	res, err := calculateAt(b, today(2024, 4, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 3 {
		t.Errorf("MultiplierPenalty = %d, want 3", res.MultiplierPenalty)
	}
}

// Anuitas: bulan jatuh tempo bukan bulan today → multiplier 1.
func TestCalculate_Anuitas_NotDueThisMonth(t *testing.T) {
	b := baseBills()
	b.Product = "KMG-DG"

	res, err := calculateAt(b, today(2025, 6, 10))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 1 {
		t.Errorf("MultiplierPenalty = %d, want 1", res.MultiplierPenalty)
	}
	if res.Penalty != 150_000 {
		t.Errorf("Penalty = %d, want 150000", res.Penalty)
	}
}

// Anuitas: jatuh tempo bulan ini → 0.
func TestCalculate_Anuitas_DueThisMonth(t *testing.T) {
	b := baseBills()
	b.Product = "KMG-DG"

	res, err := calculateAt(b, today(2026, 1, 10))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 0 {
		t.Errorf("MultiplierPenalty = %d, want 0", res.MultiplierPenalty)
	}
}

// Tipe produk lain: multiplier 0, kalkulasi tetap jalan.
func TestCalculate_UnknownProductType(t *testing.T) {
	b := baseBills()
	b.Product = "KMG-XX"

	res, err := calculateAt(b, today(2024, 5, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.MultiplierPenalty != 0 {
		t.Errorf("MultiplierPenalty = %d, want 0", res.MultiplierPenalty)
	}
}

// Bunga: kalau hari realisasi > hari today di bulan ini → tambah Interest berjalan.
func TestCalculate_Interest_RealizationLater(t *testing.T) {
	b := baseBills()
	// Realization day 25, today day 10 → 25 > 10 → tambah Interest.
	res, err := calculateAt(b, today(2024, 6, 10))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	wantBase := int64(200_000) - 50_000 + 100_000 // 250_000
	if res.PerhitunganBunga != wantBase {
		t.Errorf("Bunga = %d, want %d", res.PerhitunganBunga, wantBase)
	}
	if res.TypeBunga != "Tunggakan Bunga" {
		t.Errorf("TypeBunga = %q, want Tunggakan Bunga", res.TypeBunga)
	}
}

// Bunga: hari realisasi <= hari today → tidak tambah Interest, base jadi 150_000.
func TestCalculate_Interest_RealizationPassed(t *testing.T) {
	b := baseBills()
	res, err := calculateAt(b, today(2024, 6, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	wantBase := int64(200_000) - 50_000 // 150_000
	if res.PerhitunganBunga != wantBase {
		t.Errorf("Bunga = %d, want %d", res.PerhitunganBunga, wantBase)
	}
}

// Bunga negatif → "Titipan Bunga".
func TestCalculate_Interest_TitipanBunga(t *testing.T) {
	b := baseBills()
	b.LastInterest = 30_000
	b.Titipan = 100_000

	res, err := calculateAt(b, today(2024, 6, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.PerhitunganBunga != -70_000 {
		t.Errorf("Bunga = %d, want -70000", res.PerhitunganBunga)
	}
	if res.TypeBunga != "Titipan Bunga" {
		t.Errorf("TypeBunga = %q, want Titipan Bunga", res.TypeBunga)
	}
}

// Bunga = 0 → label "Bunga".
func TestCalculate_Interest_Zero(t *testing.T) {
	b := baseBills()
	b.LastInterest = 50_000
	b.Titipan = 50_000
	b.Interest = 0

	res, err := calculateAt(b, today(2024, 6, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	if res.PerhitunganBunga != 0 {
		t.Errorf("Bunga = %d, want 0", res.PerhitunganBunga)
	}
	if res.TypeBunga != "Bunga" {
		t.Errorf("TypeBunga = %q, want Bunga", res.TypeBunga)
	}
}

func TestCalculate_TotalPelunasan(t *testing.T) {
	b := baseBills()
	res, err := calculateAt(b, today(2024, 5, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}
	want := res.BakiDebet + res.PerhitunganBunga + res.Penalty + res.Denda
	if res.TotalPelunasan() != want {
		t.Errorf("Total = %d, want %d", res.TotalPelunasan(), want)
	}
}

func TestCalculate_NilBills(t *testing.T) {
	if _, err := Calculate(nil); err == nil {
		t.Error("expected error for nil bills")
	}
}

func TestCalculate_InvalidProduct(t *testing.T) {
	b := baseBills()
	b.Product = "X"
	if _, err := calculateAt(b, today(2024, 5, 20)); err == nil {
		t.Error("expected error for product < 2 chars")
	}
}

func TestCalculate_InvalidDate(t *testing.T) {
	b := baseBills()
	b.Realization = "15-01-2024"
	if _, err := calculateAt(b, today(2024, 5, 20)); err == nil {
		t.Error("expected error for non-ISO date")
	}
}

func TestCalculate_MissingDate(t *testing.T) {
	b := baseBills()
	b.DueDate = ""
	if _, err := calculateAt(b, today(2024, 5, 20)); err == nil {
		t.Error("expected error for empty due date")
	}
}

func TestFormatWhatsApp_ContainsKeyFields(t *testing.T) {
	b := baseBills()
	res, err := calculateAt(b, today(2024, 5, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}

	out := res.FormatWhatsApp()
	mustContain := []string{
		"DETAIL PELUNASAN KREDIT",
		"BUDI",
		"010600001234",
		"Jl. Mawar No. 1",
		"Rp 5.000.000",    // BakiDebet
		"Rp 12.000.000",   // Plafond
		"Penalty (6x)",    // multiplier
		"Tunggakan Bunga", // tipe bunga
		"Realisasi",
		"Jatuh Tempo",
	}
	for _, want := range mustContain {
		if !strings.Contains(out, want) {
			t.Errorf("FormatWhatsApp missing %q in output:\n%s", want, out)
		}
	}
}

// Sanitize: tanda * di nama harus di-escape supaya tidak break formatting WA.
func TestFormatWhatsApp_SanitizesMarkdown(t *testing.T) {
	b := baseBills()
	b.Name = "PT *PALSU*"
	b.Address = "Jl. _underscore_"
	res, err := calculateAt(b, today(2024, 5, 20))
	if err != nil {
		t.Fatalf("Calculate error: %v", err)
	}

	out := res.FormatWhatsApp()
	if !strings.Contains(out, `\*PALSU\*`) {
		t.Errorf("name not escaped: %s", out)
	}
	if !strings.Contains(out, `\_underscore\_`) {
		t.Errorf("address not escaped: %s", out)
	}
}

func TestFormatNumberID(t *testing.T) {
	cases := map[int64]string{
		0:             "0",
		1:             "1",
		100:           "100",
		1_000:         "1.000",
		1_500_000:     "1.500.000",
		-2_000:        "-2.000",
		1_000_000_000: "1.000.000.000",
	}
	for in, want := range cases {
		if got := formatNumberID(in); got != want {
			t.Errorf("formatNumberID(%d) = %q, want %q", in, got, want)
		}
	}
}

func TestMonthsBetween(t *testing.T) {
	cases := []struct {
		start, end time.Time
		want       int
	}{
		{today(2024, 1, 1), today(2024, 1, 31), 0},
		{today(2024, 1, 1), today(2024, 7, 1), 6},
		{today(2024, 1, 1), today(2025, 1, 1), 12},
		{today(2024, 1, 1), today(2026, 1, 1), 24},
	}
	for _, c := range cases {
		if got := monthsBetween(c.start, c.end); got != c.want {
			t.Errorf("monthsBetween(%s, %s) = %d, want %d",
				c.start.Format("2006-01"), c.end.Format("2006-01"), got, c.want)
		}
	}
}
