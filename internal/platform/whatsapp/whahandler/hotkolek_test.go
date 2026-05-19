package whahandler

import (
	"reflect"
	"testing"
)

func TestExtractSPKs(t *testing.T) {
	cases := []struct {
		name string
		in   string
		want []string
	}{
		{"single with prefix", ".010600001234", []string{"010600001234"}},
		{"multiple space-separated", ".010600001234 010600005678", []string{"010600001234", "010600005678"}},
		{"three spk", ".010600001234 010600005678 010600009999",
			[]string{"010600001234", "010600005678", "010600009999"}},
		{"too short ignored", ".0106000", []string{}},
		{"non-digit ignored", ".0106000ABCD12", []string{}},
		{"mixed valid+invalid", ".010600001234 abcd12345678 010600005678",
			[]string{"010600001234", "010600005678"}},
		{"empty", "", []string{}},
		{"only prefix", ".", []string{}},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := extractSPKs(c.in)
			if len(got) == 0 && len(c.want) == 0 {
				return
			}
			if !reflect.DeepEqual(got, c.want) {
				t.Errorf("extractSPKs(%q) = %v, want %v", c.in, got, c.want)
			}
		})
	}
}

func TestHotKolekMatch(t *testing.T) {
	h := &HotKolek{}

	cases := []struct {
		body string
		want bool
	}{
		{".010600001234", true},
		{".010600001234 010600005678", true},
		{".010600001234 010600005678 010600009999", true},
		{"010600001234", false},        // tidak ada prefix titik
		{".0106000", false},             // kurang dari 12 digit
		{".010600001234ABCD", false},    // ada karakter non-digit
		{".010600001234,010600005678", false}, // separator koma, bukan spasi
		{".p 010600001234", false},      // command lain
		{"halo bos", false},
		{"", false},
	}
	for _, c := range cases {
		t.Run(c.body, func(t *testing.T) {
			m := makeMsg(c.body, "62811")
			if got := h.Match(m); got != c.want {
				t.Errorf("Match(%q) = %v, want %v", c.body, got, c.want)
			}
		})
	}
}

func TestIsAllDigits(t *testing.T) {
	cases := map[string]bool{
		"":             false,
		"123":          true,
		"010600001234": true,
		"0106 0001234": false,
		"abc":          false,
		"123abc":       false,
	}
	for in, want := range cases {
		if got := isAllDigits(in); got != want {
			t.Errorf("isAllDigits(%q) = %v, want %v", in, got, want)
		}
	}
}
