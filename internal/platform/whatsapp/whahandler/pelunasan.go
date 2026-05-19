package whahandler

import (
	"context"
	"log/slog"
	"regexp"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/service/pelunasan"
)

// Pelunasan menangani perintah ".p {SPK 12 digit}".
//
// Flow:
//
//  1. Validasi format (.p + spasi + 12 digit angka).
//  2. Lookup Bills by SPK.
//  3. Kalau tidak ada → balas "data tidak ditemukan" (admin: edit pesan,
//     dengan delay 2 detik untuk meniru perilaku legacy yang memberi
//     waktu user lihat reaksi emoji dulu).
//  4. Kalau ada → hitung pelunasan, format ke pesan, kirim react +
//     edit/text sesuai admin atau bukan.
type Pelunasan struct {
	Bills    *bill.Service
	Updates  *logsvc.Service
	Sender   *whatsapp.Sender
	Router   *whatsapp.Router // untuk cek IsFromAdmin
	Reaction bool             // false untuk skip reaction (testing)
}

const (
	pelunasanPrefix = ".p "
	pelunasanFormat = ".p [SPK 12 digit]"
)

var spkPattern = regexp.MustCompile(`^\d{12}$`)

func (h *Pelunasan) Match(m *whatsapp.IncomingMessage) bool {
	return m != nil && strings.HasPrefix(m.Body, pelunasanPrefix)
}

func (h *Pelunasan) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Bills == nil {
		return
	}

	spk := strings.TrimSpace(strings.TrimPrefix(m.Body, pelunasanPrefix))
	if !spkPattern.MatchString(spk) {
		h.replyError(ctx, m, "Format SPK tidak valid. Gunakan: "+pelunasanFormat)
		return
	}

	chat := m.ChatJID()
	isAdmin := h.Router != nil && h.Router.IsFromAdmin(m)

	// Reaction emoji = "sedang diproses". Pakai info pesan inbound, bukan outbound.
	if h.Reaction {
		_ = h.Sender.React(ctx, m.Info)
	}

	b, err := h.Bills.GetByID(ctx, spk)
	if err != nil {
		slog.Error("pelunasan: query bill gagal", "spk", spk, "err", err)
		h.replyError(ctx, m, "Terjadi kesalahan sistem. Silakan coba lagi.")
		return
	}
	if b == nil {
		// Legacy: edit pesan untuk admin (efek "loading → tidak ditemukan"),
		// pesan baru untuk user biasa.
		if isAdmin {
			_ = h.Sender.EditMessage(ctx, chat, m.Info.ID, "Data tersebut tidak ditemukan")
			return
		}
		_, _ = h.Sender.SendText(ctx, chat, "Data tersebut tidak ditemukan", &m.Info)
		return
	}

	res, err := pelunasan.Calculate(b)
	if err != nil {
		slog.Error("pelunasan: kalkulasi gagal", "spk", spk, "err", err)
		h.replyError(ctx, m, "Gagal menghitung pelunasan. Silakan coba lagi.")
		return
	}

	msg := res.FormatWhatsApp()
	if h.Updates != nil {
		msg += h.Updates.WhatsAppWarning(ctx, "TAGIHAN")
	}

	if isAdmin {
		if err := h.Sender.EditMessage(ctx, chat, m.Info.ID, msg); err != nil {
			slog.Warn("pelunasan: edit pesan admin gagal, fallback kirim baru", "err", err)
			_, _ = h.Sender.SendText(ctx, chat, msg, &m.Info)
		}
		return
	}
	_, _ = h.Sender.SendText(ctx, chat, msg, &m.Info)
}

func (h *Pelunasan) replyError(ctx context.Context, m *whatsapp.IncomingMessage, reason string) {
	prefix := "❌ "
	if h.Router != nil && h.Router.IsFromAdmin(m) {
		_ = h.Sender.EditMessage(ctx, m.ChatJID(), m.Info.ID, prefix+reason)
		return
	}
	_, _ = h.Sender.SendText(ctx, m.ChatJID(), prefix+reason, &m.Info)
}
