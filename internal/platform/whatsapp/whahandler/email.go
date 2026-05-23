package whahandler

import (
	"context"
	"fmt"
	"log/slog"
	"path/filepath"
	"regexp"
	"strings"

"go.mau.fi/whatsmeow/proto/waE2E"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/email"
)

// emailRegex pola validator alamat email — sengaja dibatasi (tanpa
// quoted-local-part dll) supaya lebih konservatif daripada RFC 5322.
var emailRegex = regexp.MustCompile(`^[\w._%+\-]+@[\w.\-]+\.[a-zA-Z]{2,}$`)

// Email handler "{prefix}email" + "{prefix}done" — admin only.
//
// Sub-flow:
//
//   - "{prefix}email"                   → buka sesi (default recipient).
//   - "{prefix}email user@host"         → buka sesi (recipient custom).
//   - "{prefix}done"                    → tutup sesi, kirim email langsung.
//
// Reply-flow tidak diimplementasikan di versi Go — whatsmeow tidak
// menyediakan API "download by message ID arbitrary" yang setara dengan
// gateway HTTP legacy. User cukup forward media ke chat bot, lalu .done.
type Email struct {
	Sessions         *email.SessionCache
	Forwarder        *email.Forwarder
	DefaultRecipient string

	Sender *whatsapp.Sender
	Router *whatsapp.Router
	Prefix string // default "." kalau kosong
}

func (h *Email) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil || h.Router == nil {
		return false
	}
	if !h.Router.IsFromAdmin(m) {
		return false
	}
	body := strings.TrimSpace(m.Body)
	emailCmd := prefixed(h.Prefix, "email")
	doneCmd := prefixed(h.Prefix, "done")
	if body == doneCmd {
		return true
	}
	return body == emailCmd || strings.HasPrefix(body, emailCmd+" ")
}

func (h *Email) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Sessions == nil || h.Forwarder == nil {
		return
	}
	body := strings.TrimSpace(m.Body)
	if body == prefixed(h.Prefix, "done") {
		h.handleDone(ctx, m)
		return
	}
	h.handleStart(ctx, m)
}

func (h *Email) handleStart(ctx context.Context, m *whatsapp.IncomingMessage) {
	chat := m.ChatJID()
	body := strings.TrimSpace(m.Body)

	recipient := h.resolveRecipient(body)
	if recipient == "" {
		emailCmd := prefixed(h.Prefix, "email")
		_, _ = h.Sender.SendText(ctx, chat,
			"❌ Format email tidak valid. Gunakan: *"+emailCmd+"* atau *"+emailCmd+" user@example.com*",
			&m.Info)
		return
	}

	phone := m.SenderPhone()
	fromName := m.PushName
	if strings.TrimSpace(fromName) == "" {
		fromName = phone
	}

	sess := &email.Session{
		ChatID:      chat.String(),
		SenderPhone: phone,
		FromName:    fromName,
		Recipient:   recipient,
	}

	chatStr := chat.String()
	h.Sessions.Put(sess, func(s *email.Session) {
		slog.Info("email: TTL habis, auto-send", "phone", s.SenderPhone)
		// Auto-send berjalan di goroutine baru dari SessionCache; pakai
		// ctx baru karena ctx Handle sudah lewat.
		h.Forwarder.Send(context.Background(), s)
	})

	doneCmd := prefixed(h.Prefix, "done")
	_, _ = h.Sender.SendText(ctx, chat,
		"📧 Sesi email dibuka. Tujuan: "+recipient+"\n"+
			"Kirimkan foto, video, atau dokumen yang ingin diteruskan.\n"+
			"Ketik *"+doneCmd+"* jika sudah selesai, atau tunggu 60 detik untuk dikirim otomatis.",
		&m.Info)
	slog.Info("email: sesi dibuka", "phone", phone, "to", recipient, "chat", chatStr)
}

func (h *Email) handleDone(ctx context.Context, m *whatsapp.IncomingMessage) {
	phone := m.SenderPhone()
	chat := m.ChatJID()
	sess := h.Sessions.Remove(phone)
	if sess == nil {
		emailCmd := prefixed(h.Prefix, "email")
		_, _ = h.Sender.SendText(ctx, chat,
			"⚠️ Tidak ada sesi email aktif. Ketik *"+emailCmd+"* untuk mulai.", &m.Info)
		return
	}
	slog.Info("email: sesi ditutup user", "phone", phone, "media", sess.MediaCount())

	placeholder := fmt.Sprintf("📧 Mengirim email ke %s ...\n📎 %d file dilampirkan.",
		sess.Recipient, sess.MediaCount())
	msgID, err := h.Sender.SendText(ctx, chat, placeholder, &m.Info)
	if err != nil {
		slog.Warn("email: kirim placeholder gagal", "phone", phone, "err", err)
	}

	summary := h.Forwarder.Deliver(ctx, sess)
	if msgID == "" {
		_, _ = h.Sender.SendText(ctx, chat, summary, &m.Info)
		return
	}
	if err := h.Sender.EditMessage(ctx, chat, msgID, summary); err != nil {
		slog.Warn("email: edit pesan hasil gagal, fallback kirim baru",
			"phone", phone, "err", err)
		_, _ = h.Sender.SendText(ctx, chat, summary, &m.Info)
	}
}

func (h *Email) resolveRecipient(body string) string {
	emailCmd := prefixed(h.Prefix, "email")
	rest := strings.TrimSpace(strings.TrimPrefix(body, emailCmd))
	if rest == "" {
		return strings.TrimSpace(h.DefaultRecipient)
	}
	if emailRegex.MatchString(rest) {
		return rest
	}
	return ""
}

// EmailCollector handler kedua: kalau ada sesi email aktif, setiap pesan
// media dari pengirim yang sama → download bytes → push ke sesi.
//
// Didaftarkan SETELAH Email handler. Match cuma true kalau pesan punya
// media DAN ada sesi aktif — supaya pesan teks normal masih bisa diproses
// handler lain yang lebih spesifik (mis. .p, .t).
type EmailCollector struct {
	Sessions *email.SessionCache
	Sender   *whatsapp.Sender
}

func (h *EmailCollector) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil || h.Sessions == nil {
		return false
	}
	if m.MediaKind == "" {
		return false
	}
	return h.Sessions.Get(m.SenderPhone()) != nil
}

func (h *EmailCollector) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Sessions == nil {
		return
	}
	sess := h.Sessions.Get(m.SenderPhone())
	if sess == nil {
		return
	}

	bytes, err := h.Sender.DownloadMedia(ctx, m.Raw)
	if err != nil {
		slog.Warn("email: download media gagal", "phone", m.SenderPhone(), "err", err)
		return
	}
	if len(bytes) == 0 {
		slog.Warn("email: media kosong", "phone", m.SenderPhone())
		return
	}

	filename := resolveMediaFilename(m.Raw, m.MediaKind, sess.MediaCount()+1)
	caption := strings.TrimSpace(m.Body)
	sess.AddMedia(email.CollectedMedia{
		Filename:  filename,
		Bytes:     bytes,
		MediaType: m.MediaKind,
		Caption:   caption,
	})
	slog.Info("email: media dikoleksi",
		"phone", m.SenderPhone(), "kind", m.MediaKind,
		"file", filename, "size", len(bytes))
}

// resolveMediaFilename ambil filename dari DocumentMessage; kalau bukan
// document atau filename kosong, generate dari media kind + index.
func resolveMediaFilename(msg *waE2E.Message, kind string, index int) string {
	if msg == nil {
		return defaultMediaFilename(kind, index)
	}
	if doc := msg.GetDocumentMessage(); doc != nil {
		if name := strings.TrimSpace(doc.GetFileName()); name != "" {
			return sanitizeFilename(name)
		}
	}
	return defaultMediaFilename(kind, index)
}

func defaultMediaFilename(kind string, index int) string {
	ext := ".bin"
	switch kind {
	case "image":
		ext = ".jpg"
	case "video":
		ext = ".mp4"
	case "audio":
		ext = ".ogg"
	case "sticker":
		ext = ".webp"
	}
	if kind == "" {
		kind = "file"
	}
	return fmt.Sprintf("%s_%d%s", kind, index, ext)
}

// sanitizeFilename hapus path component supaya tidak menulis ke folder
// di luar yang diinginkan saat email client extract.
func sanitizeFilename(name string) string {
	name = filepath.Base(name)
	if name == "." || name == "/" || name == "" {
		return "attachment"
	}
	return name
}

// NewWhatsAppNotifier bikin Notifier yang kirim balik via whatsapp.Sender.
//
// chatID disimpan di Session.ChatID sebagai string hasil JID.String();
// notifier parse balik ke types.JID lewat helper di package whatsapp.
func NewWhatsAppNotifier(sender *whatsapp.Sender) email.Notifier {
	return &whatsappNotifier{sender: sender}
}

type whatsappNotifier struct {
	sender *whatsapp.Sender
}

func (n *whatsappNotifier) NotifyText(ctx context.Context, chatID, message string) {
	if n.sender == nil || strings.TrimSpace(chatID) == "" {
		return
	}
	jid, err := whatsapp.ParseJID(chatID)
	if err != nil {
		slog.Warn("email notifier: parse JID gagal", "chat_id", chatID, "err", err)
		return
	}
	if _, err := n.sender.SendText(ctx, jid, message, nil); err != nil {
		slog.Warn("email notifier: kirim balik gagal", "chat_id", chatID, "err", err)
	}
}
