package simulasiangsuran

import (
	"testing"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

func TestHitung_RecommendsCheapest(t *testing.T) {
	b := &entity.Bills{
		DayLate:      "10",
		LastInterest: 500_000,
		MinInterest:  400_000,
		MinPrincipal: 1_000_000,
	}
	got := Hitung(b)
	// A=1.5jt, B=1jt, C=1.4jt -> recommend B
	if got.RekomendasiSkenario != "B" {
		t.Errorf("expected B, got %s (totals A=%d B=%d C=%d)",
			got.RekomendasiSkenario,
			got.Skenarios[0].TotalBayar, got.Skenarios[1].TotalBayar, got.Skenarios[2].TotalBayar)
	}
	if got.TotalBayarMinimum != 1_000_000 {
		t.Errorf("expected min 1.000.000, got %d", got.TotalBayarMinimum)
	}
}

func TestHitung_PastNonPerformingThreshold(t *testing.T) {
	b := &entity.Bills{DayLate: "120", MinPrincipal: 1_000_000}
	got := Hitung(b)
	for _, s := range got.Skenarios {
		if s.Kode == "B" && s.Keterangan == "" {
			t.Error("ket B should not be empty")
		}
	}
}
