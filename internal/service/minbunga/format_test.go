package minbunga

import (
	"strings"
	"testing"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

func TestFormatMessages_Reproduce(t *testing.T) {
	wib := time.FixedZone("WIB", 7*3600)
	
	// Create simulated bills (assume database reference date is June 10, 2026)
	// TAUFIK IRAWAN has DayLate = 90 on June 10.
	bill1 := entity.Bills{
		Name:           "TAUFIK IRAWAN",
		Address:        "Nangkasawit 5/3 Kejobong Purbalingga",
		AccountOfficer: "HTY",
		Plafond:        75000000,
		DebitTray:      22737050,
		LastPrincipal:  5775450,
		LastInterest:   919350,
		MinPrincipal:   1874100,
		MinInterest:    256100,
		DayLate:        "90",
	}

	bill2 := entity.Bills{
		Name:           "PONIRAH",
		Address:        "Sidanegara 1/2 Kaligondang Purbalingga",
		AccountOfficer: "RYE",
		Plafond:        20000000,
		DebitTray:      13886935,
		LastPrincipal:  1665335,
		LastInterest:   550000,
		MinPrincipal:   554135,
		MinInterest:    150000,
		DayLate:        "90",
	}

	allBills := []entity.Bills{bill1, bill2}

	// Test 1: Query for June 10 (daysDiff = 0)
	// Reference date: June 10
	t.Run("Query June 10", func(t *testing.T) {
		targets := []time.Time{
			time.Date(2026, 6, 10, 0, 0, 0, 0, wib),
		}

		grouped := Calculate(allBills, targets)
		messages := FormatMessages(grouped, "1075")

		if len(messages) != 1 {
			t.Fatalf("expected 1 message, got %d", len(messages))
		}

		msg := messages[0]
		if !strings.Contains(msg, "*Tagihan: 10 Juni 2026* (+0 hari)") {
			t.Errorf("expected header to contain 10 Juni 2026 (+0 hari)")
		}
		if !strings.Contains(msg, "*Maks. Bayar: Rabu, 10 Juni 2026*") {
			t.Errorf("expected Maks. Bayar to be bolded Rabu, 10 Juni 2026")
		}
		if !strings.Contains(msg, "*Min. Bunga: Rp256.100*") {
			t.Errorf("expected Min. Bunga to be bolded Rp256.100")
		}
	})

	// Test 2: Query for June 11 (daysDiff = 1)
	// Reference date: June 10
	t.Run("Query June 11", func(t *testing.T) {
		targets := []time.Time{
			time.Date(2026, 6, 11, 0, 0, 0, 0, wib),
		}

		grouped := Calculate(allBills, targets)
		messages := FormatMessages(grouped, "1075")

		if len(messages) != 1 {
			t.Fatalf("expected 1 message, got %d", len(messages))
		}

		msg := messages[0]
		if !strings.Contains(msg, "*Tagihan: 11 Juni 2026* (+1 hari)") {
			t.Errorf("expected header to contain 11 Juni 2026 (+1 hari)")
		}
		if !strings.Contains(msg, "*Maks. Bayar: Rabu, 10 Juni 2026*") {
			t.Errorf("expected Maks. Bayar to be bolded Rabu, 10 Juni 2026")
		}
	})
}
