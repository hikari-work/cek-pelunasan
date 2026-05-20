package whahandler

import (
	"testing"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

func TestMinBunga_Match(t *testing.T) {
	router := whatsapp.NewRouter("62811234567")
	h := &MinBunga{Router: router}

	cases := []struct {
		name string
		body string
		from string
		want bool
	}{
		{"admin .minbunga dengan args", ".minbunga 1075 12,13", "62811234567", true},
		{"admin .minbunga exact (no space)", ".minbunga", "62811234567", true},
		{"admin .minbunga prefix only", ".minbunga ", "62811234567", true},
		{"non-admin .minbunga", ".minbunga 1075 12", "6285999", false},
		{"admin command lain", ".min 1075 12", "62811234567", false},
		{"admin .minbungaa", ".minbungaa", "62811234567", false},
		{"empty body", "", "62811234567", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := h.Match(makeMsg(c.body, c.from)); got != c.want {
				t.Errorf("Match(%q from %q) = %v, want %v", c.body, c.from, got, c.want)
			}
		})
	}
}

func TestMinBunga_MatchNilRouter(t *testing.T) {
	h := &MinBunga{Router: nil}
	if h.Match(makeMsg(".minbunga 1075 12", "62811")) {
		t.Error("nil router should not match")
	}
}

func TestMinBunga_ParseDates(t *testing.T) {
	// Anchor ke 20 Mei 2026 supaya hasil deterministik.
	fixedNow := time.Date(2026, 5, 20, 10, 0, 0, 0, minBungaWIB)
	h := &MinBunga{Now: func() time.Time { return fixedNow }}

	t.Run("comma-separated", func(t *testing.T) {
		got, err := h.parseDates("12,13,14")
		if err != nil {
			t.Fatalf("err: %v", err)
		}
		if len(got) != 3 {
			t.Fatalf("len = %d, want 3", len(got))
		}
		wantDays := []int{12, 13, 14}
		for i, d := range got {
			if d.Day() != wantDays[i] || d.Month() != time.May || d.Year() != 2026 {
				t.Errorf("got[%d] = %v, want day %d May 2026", i, d, wantDays[i])
			}
		}
	})

	t.Run("range inclusive", func(t *testing.T) {
		got, err := h.parseDates("12-15")
		if err != nil {
			t.Fatalf("err: %v", err)
		}
		if len(got) != 4 {
			t.Fatalf("len = %d, want 4", len(got))
		}
		for i, d := range got {
			if d.Day() != 12+i {
				t.Errorf("got[%d].Day = %d, want %d", i, d.Day(), 12+i)
			}
		}
	})

	t.Run("single day", func(t *testing.T) {
		got, err := h.parseDates("20")
		if err != nil {
			t.Fatalf("err: %v", err)
		}
		if len(got) != 1 || got[0].Day() != 20 {
			t.Errorf("got = %v, want [20]", got)
		}
	})

	t.Run("with whitespace", func(t *testing.T) {
		got, err := h.parseDates("12, 13 , 14")
		if err != nil {
			t.Fatalf("err: %v", err)
		}
		if len(got) != 3 {
			t.Errorf("len = %d, want 3", len(got))
		}
	})

	t.Run("range reversed", func(t *testing.T) {
		_, err := h.parseDates("15-12")
		if err == nil {
			t.Error("expected error for reversed range")
		}
	})

	t.Run("invalid number", func(t *testing.T) {
		_, err := h.parseDates("abc")
		if err == nil {
			t.Error("expected error for non-numeric")
		}
	})

	t.Run("day out of range", func(t *testing.T) {
		_, err := h.parseDates("32")
		if err == nil {
			t.Error("expected error for day=32")
		}
	})

	t.Run("day zero", func(t *testing.T) {
		_, err := h.parseDates("0")
		if err == nil {
			t.Error("expected error for day=0")
		}
	})

	t.Run("invalid day for month", func(t *testing.T) {
		// Anchor ke Februari 2026 (28 hari, non-leap).
		feb := &MinBunga{Now: func() time.Time {
			return time.Date(2026, 2, 10, 0, 0, 0, 0, minBungaWIB)
		}}
		_, err := feb.parseDates("31")
		if err == nil {
			t.Error("expected error for 31 Feb")
		}
	})
}
