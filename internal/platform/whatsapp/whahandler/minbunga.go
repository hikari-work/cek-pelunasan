package whahandler

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/minbunga"
)

// MinBunga menangani perintah "{prefix}minbunga {cabang} {tanggal}" — admin only.
//
// Format tanggal: "12,13,14" (list), "12-15" (range inklusif), atau "12" (satu).
// Tahun + bulan diambil dari today (zona WIB).
//
// Flow:
//
//  1. Validasi admin (router.IsFromAdmin).
//  2. Parse args, validasi format.
//  3. Hitung threshold dayLate, query Bills by branch.
//  4. Calculate kelompok per tanggal target → format pesan → kirim
//     (boleh banyak pesan kalau hasil panjang).
type MinBunga struct {
	Bills  *bill.Service
	Sender *whatsapp.Sender
	Router *whatsapp.Router
	Prefix string // default "." kalau kosong

	// Now opsional untuk testing — return waktu "sekarang" zona WIB.
	// Default time.Now().In(WIB).
	Now func() time.Time
}

var minBungaWIB = time.FixedZone("WIB", 7*3600)

func (h *MinBunga) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil || h.Router == nil {
		return false
	}
	cmd := prefixed(h.Prefix, "minbunga")
	body := strings.TrimSpace(m.Body)
	if !matchCommand(body, cmd) {
		return false
	}
	return h.Router.IsFromAdmin(m)
}

func (h *MinBunga) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Bills == nil {
		return
	}

	chat := m.ChatJID()
	cmd := prefixed(h.Prefix, "minbunga")
	args := strings.TrimSpace(strings.TrimPrefix(m.Body, cmd))
	if args == "" {
		_, _ = h.Sender.SendText(ctx, chat,
			"Format: "+cmd+" <cabang> <tanggal>\nContoh: "+cmd+" 1075 12,13,14", &m.Info)
		return
	}

	parts := strings.SplitN(args, " ", 2)
	if len(parts) < 2 {
		_, _ = h.Sender.SendText(ctx, chat,
			"Format: "+cmd+" <cabang> <tanggal>\nContoh: "+cmd+" 1075 12,13,14", &m.Info)
		return
	}

	branch := strings.TrimSpace(parts[0])
	dateArg := strings.TrimSpace(parts[1])
	if branch == "" || dateArg == "" {
		_, _ = h.Sender.SendText(ctx, chat,
			"Format: "+cmd+" <cabang> <tanggal>\nContoh: "+cmd+" 1075 12,13,14", &m.Info)
		return
	}

	targetDates, err := h.parseDates(dateArg)
	if err != nil {
		_, _ = h.Sender.SendText(ctx, chat,
			"Format tanggal tidak valid. Contoh: 12,13,14 atau 12-15", &m.Info)
		return
	}

	slog.Info("minbunga wa: query", "branch", branch, "dates", targetDates)

	minDayLate := minbunga.MinDayLateThreshold(targetDates)
	allBills, err := h.Bills.FindMinimalBungaByBranch(ctx, branch, minDayLate)
	if err != nil {
		slog.Error("minbunga wa: query bill gagal", "branch", branch, "err", err)
		_, _ = h.Sender.SendText(ctx, chat, "❌ Gagal mengambil data tagihan.", &m.Info)
		return
	}
	if len(allBills) == 0 {
		_, _ = h.Sender.SendText(ctx, chat,
			"Tidak ada data tagihan untuk cabang "+branch+".", &m.Info)
		return
	}

	grouped := minbunga.Calculate(allBills, targetDates)
	messages := minbunga.FormatMessages(grouped, branch)
	for _, msg := range messages {
		if _, err := h.Sender.SendText(ctx, chat, msg, nil); err != nil {
			slog.Error("minbunga wa: kirim pesan gagal", "err", err)
			return
		}
	}
	slog.Info("minbunga wa: selesai", "branch", branch, "messages", len(messages), "chat", chat.String())
}

// parseDates urai argumen tanggal: "12,13,14" / "12-15" / "12".
// Semua tanggal di-anchor ke bulan + tahun "now" zona WIB.
//
// Return error kalau ada angka tidak valid, range terbalik (from > to),
// hari di luar 1..31, atau tanggal tidak ada di bulan tersebut (mis. 31 Feb).
func (h *MinBunga) parseDates(dateArg string) ([]time.Time, error) {
	now := h.now()
	year := now.Year()
	month := now.Month()

	var days []int
	switch {
	case strings.Contains(dateArg, ","):
		for _, p := range strings.Split(dateArg, ",") {
			d, err := parseDay(p)
			if err != nil {
				return nil, err
			}
			days = append(days, d)
		}
	case strings.Contains(dateArg, "-"):
		rangeParts := strings.SplitN(dateArg, "-", 2)
		from, err := parseDay(rangeParts[0])
		if err != nil {
			return nil, err
		}
		to, err := parseDay(rangeParts[1])
		if err != nil {
			return nil, err
		}
		if from > to {
			return nil, errors.New("range tanggal terbalik")
		}
		for d := from; d <= to; d++ {
			days = append(days, d)
		}
	default:
		d, err := parseDay(dateArg)
		if err != nil {
			return nil, err
		}
		days = append(days, d)
	}

	if len(days) == 0 {
		return nil, errors.New("tidak ada tanggal yang diparse")
	}

	out := make([]time.Time, 0, len(days))
	for _, d := range days {
		t := time.Date(year, month, d, 0, 0, 0, 0, minBungaWIB)
		// Kalau time.Date di-rollover (mis. 31 Feb → 3 Mar), tolak —
		// legacy throw DateTimeException untuk kasus ini.
		if t.Day() != d || t.Month() != month {
			return nil, fmt.Errorf("tanggal %d tidak valid untuk bulan %s", d, month)
		}
		out = append(out, t)
	}
	return out, nil
}

func (h *MinBunga) now() time.Time {
	if h.Now != nil {
		return h.Now()
	}
	return time.Now().In(minBungaWIB)
}

func parseDay(s string) (int, error) {
	n, err := strconv.Atoi(strings.TrimSpace(s))
	if err != nil {
		return 0, err
	}
	if n < 1 || n > 31 {
		return 0, fmt.Errorf("hari di luar rentang: %d", n)
	}
	return n, nil
}
