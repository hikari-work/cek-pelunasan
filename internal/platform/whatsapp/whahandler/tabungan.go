package whahandler

import (
	"context"
	"fmt"
	"log/slog"
	"regexp"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// Tabungan menangani perintah "{prefix}t {input}":
//
//   - {input} 12 digit angka  → detail satu rekening (FindByID).
//   - selain itu              → cari nama, hasil dibatasi 5 rekening teratas.
//
// Admin: balasan via edit pesan asli (efek "loading → hasil"). User biasa:
// pesan baru sebagai reply.
//
// React emoji dikirim sebelum balasan utama hanya saat hit by ID, sama
// dengan legacy supaya UX konsisten.
type Tabungan struct {
	Service *savings.Service
	Updates *logsvc.Service
	Sender  *whatsapp.Sender
	Router  *whatsapp.Router
	Prefix  string // default "." kalau kosong
}

const tabunganMaxResults = 5

var accountPattern = regexp.MustCompile(`^\d{12}$`)

func (h *Tabungan) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil {
		return false
	}
	return strings.HasPrefix(m.Body, prefixed(h.Prefix, "t")+" ")
}

func (h *Tabungan) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Service == nil {
		return
	}

	cmd := prefixed(h.Prefix, "t")
	input := strings.TrimSpace(strings.TrimPrefix(m.Body, cmd+" "))
	if input == "" {
		h.replyError(ctx, m, "Format: "+cmd+" [nomor rekening 12 digit] atau "+cmd+" [nama]")
		return
	}

	if accountPattern.MatchString(input) {
		h.byAccount(ctx, m, input)
		return
	}
	h.byName(ctx, m, input)
}

func (h *Tabungan) byAccount(ctx context.Context, m *whatsapp.IncomingMessage, tabID string) {
	saving, err := h.Service.FindByID(ctx, tabID)
	if err != nil {
		slog.Error("tabungan: query by id gagal", "tabId", tabID, "err", err)
		h.replyError(ctx, m, "Terjadi kesalahan sistem. Silakan coba lagi.")
		return
	}
	if saving == nil {
		_, _ = h.Sender.SendText(ctx, m.ChatJID(), "Data tidak ditemukan.", &m.Info)
		return
	}

	msg := formatSavingDetailWA(saving)
	if h.Updates != nil {
		msg += h.Updates.WhatsAppWarning(ctx, "SAVING")
	}

	// Reaction "diproses" — by-id flow yang punya reaction (legacy).
	_ = h.Sender.React(ctx, m.Info)

	if h.Router != nil && h.Router.IsFromAdmin(m) {
		if err := h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID, msg); err != nil {
			slog.Warn("tabungan: edit pesan admin gagal, fallback kirim baru", "err", err)
			_, _ = h.Sender.SendText(ctx, m.ChatJID(), msg, &m.Info)
		}
		return
	}
	_, _ = h.Sender.SendText(ctx, m.ChatJID(), msg, &m.Info)
}

func (h *Tabungan) byName(ctx context.Context, m *whatsapp.IncomingMessage, name string) {
	results, err := h.Service.FindByName(ctx, name, tabunganMaxResults)
	if err != nil {
		slog.Error("tabungan: query by name gagal", "name", name, "err", err)
		h.replyError(ctx, m, "Terjadi kesalahan sistem. Silakan coba lagi.")
		return
	}

	msg := formatNameSearchWA(name, results, tabunganMaxResults)
	if h.Updates != nil {
		msg += h.Updates.WhatsAppWarning(ctx, "SAVING")
	}
	_, _ = h.Sender.SendText(ctx, m.ChatJID(), msg, &m.Info)
}

func (h *Tabungan) replyError(ctx context.Context, m *whatsapp.IncomingMessage, reason string) {
	prefix := "❌ "
	if h.Router != nil && h.Router.IsFromAdmin(m) {
		_ = h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID, prefix+reason)
		return
	}
	_, _ = h.Sender.SendText(ctx, m.ChatJID(), prefix+reason, &m.Info)
}

// formatSavingDetailWA versi WhatsApp dari detail tabungan. Sengaja
// terpisah dari savings.FormatDetail (yang Markdown Telegram, pakai
// backtick + bullet). Di WA pakai bullet emoji + bold WA (*..*), bukan
// backtick monospace.
func formatSavingDetailWA(s *entity.Savings) string {
	book := s.Balance + s.Transaction
	effective := book - s.MinimumBalance - s.BlockingBalance
	var b strings.Builder
	_, _ = fmt.Fprintf(&b, "👤 *%s*\n", s.Name)
	_, _ = fmt.Fprintf(&b, "No. Rek: %s\n", s.TabID)
	_, _ = fmt.Fprintf(&b, "Alamat : %s\n\n", s.Address)
	b.WriteString("💰 Saldo:\n")
	_, _ = fmt.Fprintf(&b, "• Buku    : %s\n", utils.FormatRupiah(book))
	_, _ = fmt.Fprintf(&b, "• Min     : %s\n", utils.FormatRupiah(s.MinimumBalance))
	_, _ = fmt.Fprintf(&b, "• Block   : %s\n", utils.FormatRupiah(s.BlockingBalance))
	_, _ = fmt.Fprintf(&b, "• Efektif : *%s*\n", utils.FormatRupiah(effective))
	return b.String()
}

func formatNameSearchWA(query string, results []entity.Savings, max int) string {
	if len(results) == 0 {
		return "❌ Tidak ditemukan nasabah dengan nama *" + query + "*"
	}
	var b strings.Builder
	_, _ = fmt.Fprintf(&b, "🔍 *Hasil pencarian: \"%s\"*\n", query)
	_, _ = fmt.Fprintf(&b, "Ditemukan: %d nasabah\n", len(results))
	b.WriteString("━━━━━━━━━━━━━━━━━\n\n")
	for i := range results {
		b.WriteString(formatSavingDetailWA(&results[i]))
		b.WriteString("\n")
	}
	if len(results) >= max {
		_, _ = fmt.Fprintf(&b, "_Hasil dibatasi %d nasabah._\n", max)
	}
	b.WriteString("\n💡 Gunakan `.t {nomor rekening}` untuk detail lengkap.")
	return b.String()
}
