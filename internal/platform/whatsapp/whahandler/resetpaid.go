package whahandler

import (
	"context"
	"log/slog"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/hotkolek"
)

// ResetPaid menangani perintah "{prefix}resetpaid" dari admin.
//
// Hapus semua dokumen di koleksi paying (flag "sudah dibayar hari ini")
// lalu kirim rekap baru — output identik dengan handler .NNNNNNNNNNNN
// supaya admin langsung tahu kondisi setelah reset.
type ResetPaid struct {
	Service *hotkolek.Service
	Sender  *whatsapp.Sender
	Router  *whatsapp.Router
	Prefix  string
}

func (h *ResetPaid) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil || h.Router == nil {
		return false
	}
	if strings.TrimSpace(m.Body) != prefixed(h.Prefix, "resetpaid") {
		return false
	}
	return h.Router.IsFromAdmin(m)
}

func (h *ResetPaid) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Service == nil {
		return
	}

	go func() {
		if err := h.Sender.React(ctx, m.Info); err != nil {
			slog.Warn("resetpaid: reaction gagal", "err", err)
		}
	}()

	deleted, err := h.Service.ResetAllPaying(ctx)
	if err != nil {
		slog.Error("resetpaid: hapus paying gagal", "err", err)
		_, _ = h.Sender.SendText(ctx, m.ChatJID(), "❌ Gagal reset data paid. Silakan coba lagi.", &m.Info)
		return
	}
	slog.Info("resetpaid: reset selesai", "deleted", deleted, "sender", m.Info.Sender.String())

	locations, err := buildHotKolekLocations(ctx, h.Service)
	if err != nil {
		slog.Error("resetpaid: build rekap gagal", "err", err)
		_, _ = h.Sender.SendText(ctx, m.ChatJID(), "❌ Gagal mengambil rekap. Silakan coba lagi.", &m.Info)
		return
	}

	now := time.Now().In(time.FixedZone("WIB", 7*3600))
	msg := hotkolek.FormatHotKolekMessage(locations, now)
	if _, err := h.Sender.SendText(ctx, m.ChatJID(), msg, nil); err != nil {
		slog.Error("resetpaid: kirim rekap gagal", "err", err)
	}
}
