package whahandler

import (
	"testing"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

// TestPrefix_CustomBang memastikan handler match command dengan prefix "!"
// kalau dikonfigurasi via field Prefix.
func TestPrefix_CustomBang(t *testing.T) {
	router := whatsapp.NewRouter("62811234567")

	cases := []struct {
		name    string
		handler whatsapp.Handler
		body    string
		from    string
		want    bool
	}{
		// Pelunasan: tidak admin-gated, prefix di field.
		{"pelunasan !p", &Pelunasan{Prefix: "!"}, "!p 010600001234", "62811", true},
		{"pelunasan .p tidak match", &Pelunasan{Prefix: "!"}, ".p 010600001234", "62811", false},

		// Tabungan: tidak admin-gated.
		{"tabungan !t", &Tabungan{Prefix: "!"}, "!t budi", "62811", true},
		{"tabungan .t tidak match", &Tabungan{Prefix: "!"}, ".t budi", "62811", false},

		// VirtualAccount: tidak admin-gated.
		{"va !va", &VirtualAccount{Prefix: "!"}, "!va 010600001234", "62811", true},

		// JatuhBayar: admin-gated.
		{"jb !jb admin", &JatuhBayar{Router: router, Prefix: "!"}, "!jb", "62811234567", true},
		{"jb .jb admin tidak match", &JatuhBayar{Router: router, Prefix: "!"}, ".jb", "62811234567", false},
		{"jb !jb non-admin", &JatuhBayar{Router: router, Prefix: "!"}, "!jb", "6285999", false},

		// MinBunga: admin-gated.
		{"minbunga !minbunga admin", &MinBunga{Router: router, Prefix: "!"}, "!minbunga 1075 12", "62811234567", true},
		{"minbunga .minbunga admin tidak match", &MinBunga{Router: router, Prefix: "!"}, ".minbunga 1075 12", "62811234567", false},

		// Slik: tidak admin-gated.
		{"slik !slik", &Slik{Prefix: "!"}, "!slik budi", "62811", true},
		{"slik .slik tidak match", &Slik{Prefix: "!"}, ".slik budi", "62811", false},
	}

	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := c.handler.Match(makeMsg(c.body, c.from)); got != c.want {
				t.Errorf("Match(%q from %q) = %v, want %v", c.body, c.from, got, c.want)
			}
		})
	}
}

// TestPrefix_DefaultFallback memastikan handler tetap match "." kalau Prefix kosong
// (backward compat untuk test existing).
func TestPrefix_DefaultFallback(t *testing.T) {
	cases := []struct {
		name    string
		handler whatsapp.Handler
		body    string
		want    bool
	}{
		{"pelunasan default", &Pelunasan{}, ".p 010600001234", true},
		{"tabungan default", &Tabungan{}, ".t budi", true},
		{"va default", &VirtualAccount{}, ".va 010600001234", true},
		{"slik default", &Slik{}, ".slik budi", true},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := c.handler.Match(makeMsg(c.body, "62811")); got != c.want {
				t.Errorf("Match(%q) = %v, want %v", c.body, got, c.want)
			}
		})
	}
}

// TestPrefix_Email_CustomBang validasi Email + .done dengan prefix custom.
func TestPrefix_Email_CustomBang(t *testing.T) {
	router := whatsapp.NewRouter("62811234567")
	h := &Email{Router: router, Prefix: "!"}

	cases := []struct {
		body string
		from string
		want bool
	}{
		{"!email", "62811234567", true},
		{"!email user@example.com", "62811234567", true},
		{"!done", "62811234567", true},
		{".email", "62811234567", false},
		{".done", "62811234567", false},
		{"!email", "6285999", false},
	}
	for _, c := range cases {
		if got := h.Match(makeMsg(c.body, c.from)); got != c.want {
			t.Errorf("Match(%q from %q) = %v, want %v", c.body, c.from, got, c.want)
		}
	}
}

// TestPrefix_Email_ResolveRecipient cek resolver pakai prefix custom.
func TestPrefix_Email_ResolveRecipient(t *testing.T) {
	h := &Email{Prefix: "!", DefaultRecipient: "default@example.com"}
	cases := map[string]string{
		"!email":                  "default@example.com",
		"!email user@example.com": "user@example.com",
		"!email invalid":          "",
	}
	for in, want := range cases {
		if got := h.resolveRecipient(in); got != want {
			t.Errorf("resolveRecipient(%q) = %q, want %q", in, got, want)
		}
	}
}

// TestPrefix_HotKolek_Custom validasi pattern HotKolek pakai prefix custom.
func TestPrefix_HotKolek_Custom(t *testing.T) {
	h := &HotKolek{Prefix: "!"}
	cases := map[string]bool{
		"!010600001234":              true,
		"!010600001234 010600005678": true,
		".010600001234":              false, // wrong prefix
		"010600001234":               false, // no prefix
	}
	for body, want := range cases {
		if got := h.Match(makeMsg(body, "62811")); got != want {
			t.Errorf("Match(%q) = %v, want %v", body, got, want)
		}
	}
}

// TestPrefix_HotKolek_Default fallback ke "." kalau Prefix kosong.
func TestPrefix_HotKolek_Default(t *testing.T) {
	h := &HotKolek{}
	if !h.Match(makeMsg(".010600001234", "62811")) {
		t.Error("default prefix should match .010600001234")
	}
	if h.Match(makeMsg("!010600001234", "62811")) {
		t.Error("default prefix should not match !010600001234")
	}
}

// TestPrefix_ExtractSPKs_Custom strip prefix custom.
func TestPrefix_ExtractSPKs_Custom(t *testing.T) {
	got := extractSPKs("!010600001234 010600005678", "!")
	want := []string{"010600001234", "010600005678"}
	if len(got) != len(want) {
		t.Fatalf("len = %d, want %d", len(got), len(want))
	}
	for i := range got {
		if got[i] != want[i] {
			t.Errorf("got[%d] = %q, want %q", i, got[i], want[i])
		}
	}
}
