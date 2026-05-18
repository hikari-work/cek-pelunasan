package whatsapp

import (
	"context"
	"log/slog"
	"strings"
)

// pendingCommandHandler reply pesan "fitur sedang dimigrasikan" untuk
// command yang belum di-port. Tujuannya supaya user dapat balasan, bukan diam.
type pendingCommandHandler struct {
	prefix  string // mis. ".p", ".t", ".slik"
	desc    string
	sender  *Sender
}

func (h *pendingCommandHandler) Match(w *Webhook) bool {
	if w == nil || w.Payload == nil {
		return false
	}
	body := w.Payload.Body
	return strings.HasPrefix(body, h.prefix)
}

func (h *pendingCommandHandler) Handle(ctx context.Context, w *Webhook) {
	if h.sender == nil {
		return
	}
	to := w.From
	msg := "🚧 Perintah `" + h.prefix + "` (" + h.desc + ") masih dimigrasikan ke versi Go.\n" +
		"Mohon coba lagi setelah migrasi selesai."
	if err := h.sender.SendText(ctx, to, msg, w.Payload.ID); err != nil {
		slog.Warn("pending whatsapp reply failed", "prefix", h.prefix, "err", err)
	}
}

// PendingCommand bikin handler stub untuk prefix WhatsApp tertentu.
func PendingCommand(prefix, desc string, sender *Sender) Handler {
	return &pendingCommandHandler{prefix: prefix, desc: desc, sender: sender}
}

// hotKolekStub: handler khusus untuk pola .DDDDDDDDDDDD.
type hotKolekStub struct{ sender *Sender }

func (h *hotKolekStub) Match(w *Webhook) bool {
	if w == nil || w.Payload == nil {
		return false
	}
	return HotKolekPattern.MatchString(w.Payload.Body)
}

func (h *hotKolekStub) Handle(ctx context.Context, w *Webhook) {
	if h.sender == nil {
		return
	}
	_ = h.sender.SendText(ctx,
		w.From,
		"🚧 Hot Kolek (`.<12-digit-spk>`) masih dimigrasikan ke versi Go.",
		w.Payload.ID,
	)
}

// PendingHotKolek bikin handler stub untuk pola hot kolek.
func PendingHotKolek(sender *Sender) Handler {
	return &hotKolekStub{sender: sender}
}
