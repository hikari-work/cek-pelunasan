package whahandler

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// JatuhBayar — perintah "{prefix}jb" dari admin: kirim reminder jatuh bayar
// harian per Account Officer untuk cabang Kaligondang (1075).
//
// Flow:
//
//  1. Admin-only — non-admin diabaikan (tidak ada balasan).
//  2. Ambil semua tagihan cabang 1075, filter PayDown == hari ini.
//  3. Group per AccountOfficer.
//  4. Untuk setiap AO yang punya tagihan, kirim 1 pesan reminder.
//     Pesan pertama via EditMessage (replace ".jb" admin), berikutnya via
//     SendText baru. Sleep 1 detik antar pesan untuk hindari rate limit.
//
// Untuk tiap nasabah, lookup nomor HP dari Savings by CIF — kalau gagal/
// kosong tampilkan "-".
type JatuhBayar struct {
	Bills   *bill.Service
	Savings *savings.Service
	Sender  *whatsapp.Sender
	Router  *whatsapp.Router
	Prefix  string // default "." kalau kosong
}

const jbBranchCode = "1075"

func (h *JatuhBayar) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil || h.Router == nil {
		return false
	}
	body := strings.TrimSpace(m.Body)
	if !matchCommand(body, prefixed(h.Prefix, "jb")) {
		return false
	}
	return h.Router.IsFromAdmin(m)
}

func (h *JatuhBayar) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Bills == nil {
		return
	}

	bills, err := h.Bills.FindAllByBranch(ctx, jbBranchCode)
	if err != nil {
		slog.Error("jb: query bills gagal", "err", err)
		_ = h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID,
			"❌ Gagal mengambil data tagihan.")
		return
	}

	today := time.Now().In(time.FixedZone("WIB", 7*3600))
	dayOfMonth := strconv.Itoa(today.Day())

	grouped := groupByAOForToday(bills, dayOfMonth)
	if len(grouped) == 0 {
		_ = h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID,
			"Tidak ada nasabah jatuh bayar hari ini.")
		return
	}

	first := true
	for ao, list := range grouped {
		msg := h.formatJatuhBayar(ctx, list, ao, today)
		if first {
			if err := h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID, msg); err != nil {
				slog.Warn("jb: edit pesan pertama gagal, fallback kirim baru", "err", err)
				_, _ = h.Sender.SendText(ctx, m.ChatJID(), msg, nil)
			}
			first = false
		} else {
			if _, err := h.Sender.SendText(ctx, m.ChatJID(), msg, nil); err != nil {
				slog.Warn("jb: kirim pesan AO gagal", "ao", ao, "err", err)
			}
		}
		// Throttle 1 detik supaya gateway WA tidak rate-limit. Stop kalau
		// ctx batal.
		select {
		case <-ctx.Done():
			return
		case <-time.After(1 * time.Second):
		}
	}
}

// groupByAOForToday filter bills yang PayDown sama dengan dayOfMonth
// (string langsung — legacy compare string-vs-string, bukan numeric),
// lalu group per AccountOfficer. Bills dengan AO kosong di-skip.
func groupByAOForToday(bills []entity.Bills, dayOfMonth string) map[string][]entity.Bills {
	out := make(map[string][]entity.Bills)
	for _, b := range bills {
		if strings.TrimSpace(b.AccountOfficer) == "" {
			continue
		}
		if b.PayDown != dayOfMonth {
			continue
		}
		out[b.AccountOfficer] = append(out[b.AccountOfficer], b)
	}
	return out
}

// formatJatuhBayar render satu pesan reminder per AO. Sama dengan legacy:
// header tanggal + AO + total nasabah, lalu daftar nomor + SPK + tunggakan
// (kalau ada) atau angsuran + nomor HP (lookup dari Savings by CIF).
func (h *JatuhBayar) formatJatuhBayar(ctx context.Context, bills []entity.Bills, ao string, today time.Time) string {
	if len(bills) == 0 {
		return ""
	}
	var b strings.Builder
	b.WriteString("🔔 *REMINDER JATUH BAYAR*\n")
	fmt.Fprintf(&b, "📅 Tanggal: %s\n", today.Format("2006-01-02"))
	fmt.Fprintf(&b, "👤 AO: *%s*\n", ao)
	fmt.Fprintf(&b, "📊 Total Nasabah: %d orang\n\n", len(bills))

	for i, bill := range bills {
		fmt.Fprintf(&b, "*%d. %s*\n", i+1, bill.Name)
		fmt.Fprintf(&b, "   💳 No SPK : %s\n", bill.NoSpk)

		if bill.LastInstallment > 0 {
			fmt.Fprintf(&b, "   ⚠️ Tunggakan: %s\n", utils.FormatRupiah(bill.LastInstallment))
		} else {
			fmt.Fprintf(&b, "   💰 Angsuran: %s\n", utils.FormatRupiah(bill.Installment))
		}

		phone := h.lookupPhone(ctx, bill.CustomerID)
		fmt.Fprintf(&b, "   📱 No HP: %s\n", phone)
		b.WriteString("\n")
	}

	b.WriteString("═══════════════════\n")
	b.WriteString("Harap segera lakukan follow up kepada nasabah terkait.\n")
	b.WriteString("_Pesan otomatis dari sistem_")
	return b.String()
}

func (h *JatuhBayar) lookupPhone(ctx context.Context, cif string) string {
	if h.Savings == nil || cif == "" {
		return "-"
	}
	s, err := h.Savings.FindByCIF(ctx, cif)
	if err != nil {
		slog.Debug("jb: lookup phone gagal", "cif", cif, "err", err)
		return "Error retrieving"
	}
	if s == nil || strings.TrimSpace(s.Phone) == "" {
		return "-"
	}
	return s.Phone
}
