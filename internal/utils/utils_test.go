package utils

import (
	"strings"
	"testing"
)

func TestFormatRupiah(t *testing.T) {
	cases := map[int64]string{
		0:        "Rp0",
		100:      "Rp100",
		1500:     "Rp1.500",
		1500000:  "Rp1.500.000",
		-2500000: "-Rp2.500.000",
	}
	for in, want := range cases {
		if got := FormatRupiah(in); got != want {
			t.Errorf("FormatRupiah(%d) = %q, want %q", in, got, want)
		}
	}
}

func TestFormatPhoneNumber(t *testing.T) {
	cases := map[string]string{
		"":            "📵 Tidak tersedia",
		"   ":         "📵 Tidak tersedia",
		"81234567890": "📱 0812-3456-7890",
		"021456789":   "☎️ 0214-5678-9",
	}
	for in, want := range cases {
		got := FormatPhoneNumber(in)
		if got != want {
			t.Errorf("FormatPhoneNumber(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestSystemSummary_Format(t *testing.T) {
	got := SystemSummary()
	if !strings.Contains(got, "%") || !strings.Contains(got, "MB /") {
		t.Errorf("SystemSummary() = %q, missing pieces", got)
	}
}
