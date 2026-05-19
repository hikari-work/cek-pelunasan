package whahandler

import (
	"strings"
	"testing"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

// --- Virtual Account ---

func TestVA_Match(t *testing.T) {
	h := &VirtualAccount{}
	cases := []struct {
		body string
		want bool
	}{
		{".va 010600001234", true},
		{".va ", true},
		{".va", false},
		{"va 010600001234", false},
		{".vat 010600001234", false},
		{"", false},
	}
	for _, c := range cases {
		if got := h.Match(makeMsg(c.body, "62811")); got != c.want {
			t.Errorf("Match(%q) = %v, want %v", c.body, got, c.want)
		}
	}
}

func TestVA_Mandiri(t *testing.T) {
	got, err := vaMandiri("010600001234")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	want := "86219 1 0106 001234"
	if got != want {
		t.Errorf("vaMandiri = %q, want %q", got, want)
	}
}

func TestVA_BRI(t *testing.T) {
	got, err := vaBRI("010600001234")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	want := "14654 0106 001234"
	if got != want {
		t.Errorf("vaBRI = %q, want %q", got, want)
	}
}

func TestVA_Danamon(t *testing.T) {
	got, err := vaDanamon("010600001234")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	want := "7997 0106 00 001234"
	if got != want {
		t.Errorf("vaDanamon = %q, want %q", got, want)
	}
}

func TestVA_BNI(t *testing.T) {
	got, err := vaBNI("010600001234")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	want := "8743 0106 00 001234"
	if got != want {
		t.Errorf("vaBNI = %q, want %q", got, want)
	}
}

func TestVA_TooShort(t *testing.T) {
	for name, fn := range map[string]vaFormatter{
		"mandiri": vaMandiri,
		"bri":     vaBRI,
		"danamon": vaDanamon,
		"bni":     vaBNI,
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := fn("12345"); err == nil {
				t.Errorf("%s: expected error for short number", name)
			}
		})
	}
}

func TestBuildVAMessage(t *testing.T) {
	got := buildVAMessage("BUDI", "010600001234", "Jl. Mawar")
	mustContain := []string{
		"*Informasi Akun*",
		"No SPK: _010600001234_",
		"Nama: _BUDI_",
		"Alamat: _Jl. Mawar_",
		"Mandiri",
		"BRI (BRIVA)",
		"Danamon",
		"BNI",
		"86219 1 0106 001234",
		"14654 0106 001234",
	}
	for _, w := range mustContain {
		if !strings.Contains(got, w) {
			t.Errorf("missing %q in:\n%s", w, got)
		}
	}
}

// --- Jatuh Bayar ---

func TestJB_Match(t *testing.T) {
	router := whatsapp.NewRouter("62811234567")
	h := &JatuhBayar{Router: router}

	cases := []struct {
		name string
		body string
		from string
		want bool
	}{
		{"admin .jb exact", ".jb", "62811234567", true},
		{"admin .jb dengan arg", ".jb extra", "62811234567", true},
		{"non-admin .jb", ".jb", "6285999", false},
		{"admin tanpa prefix", "halo", "62811234567", false},
		{"admin command lain", ".jbb", "62811234567", false},
		{"admin .jbjb", ".jbjb", "62811234567", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := h.Match(makeMsg(c.body, c.from)); got != c.want {
				t.Errorf("Match(%q from %q) = %v, want %v", c.body, c.from, got, c.want)
			}
		})
	}
}

func TestJB_MatchNilRouter(t *testing.T) {
	h := &JatuhBayar{Router: nil}
	if h.Match(makeMsg(".jb", "62811")) {
		t.Error("nil router should not match")
	}
}

func TestGroupByAOForToday(t *testing.T) {
	bills := []entity.Bills{
		{NoSpk: "1", AccountOfficer: "AO-A", PayDown: "5"},
		{NoSpk: "2", AccountOfficer: "AO-A", PayDown: "5"},
		{NoSpk: "3", AccountOfficer: "AO-B", PayDown: "5"},
		{NoSpk: "4", AccountOfficer: "AO-A", PayDown: "10"}, // beda hari → skip
		{NoSpk: "5", AccountOfficer: "", PayDown: "5"},      // AO kosong → skip
	}
	grouped := groupByAOForToday(bills, "5")
	if len(grouped) != 2 {
		t.Fatalf("expected 2 AO groups, got %d", len(grouped))
	}
	if len(grouped["AO-A"]) != 2 {
		t.Errorf("AO-A should have 2 bills, got %d", len(grouped["AO-A"]))
	}
	if len(grouped["AO-B"]) != 1 {
		t.Errorf("AO-B should have 1 bill, got %d", len(grouped["AO-B"]))
	}
}

func TestJB_FormatJatuhBayar_NoBills(t *testing.T) {
	h := &JatuhBayar{}
	out := h.formatJatuhBayar(nil, nil, "AO-A", time.Date(2026, 5, 20, 0, 0, 0, 0, time.UTC))
	if out != "" {
		t.Errorf("expected empty output for empty bills, got %q", out)
	}
}

func TestJB_FormatJatuhBayar_WithBills(t *testing.T) {
	h := &JatuhBayar{}
	bills := []entity.Bills{
		{NoSpk: "010600001234", Name: "BUDI", Installment: 500_000, CustomerID: "C1"},
		{NoSpk: "010600005678", Name: "DEDI", LastInstallment: 750_000, CustomerID: "C2"},
	}
	out := h.formatJatuhBayar(nil, bills, "AO-X", time.Date(2026, 5, 20, 0, 0, 0, 0, time.UTC))

	mustContain := []string{
		"REMINDER JATUH BAYAR",
		"📅 Tanggal: 2026-05-20",
		"👤 AO: *AO-X*",
		"Total Nasabah: 2 orang",
		"BUDI",
		"010600001234",
		"💰 Angsuran: Rp500.000",
		"DEDI",
		"010600005678",
		"⚠️ Tunggakan: Rp750.000",
		"Pesan otomatis dari sistem",
	}
	for _, w := range mustContain {
		if !strings.Contains(out, w) {
			t.Errorf("missing %q in:\n%s", w, out)
		}
	}
}
