package whatsapp

import (
	"strings"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
)

// IncomingMessage adalah view netral atas event pesan WhatsApp yang sudah
// dipreparasi untuk handler. Layer atas tidak perlu tahu *events.Message
// atau *waE2E.Message — cukup field di sini.
//
// Field yang menunjuk ke struct whatsmeow asli (Info, Raw) tetap diekspos
// supaya sender bisa BuildEdit/BuildReaction tanpa kita salin manual semua
// field-nya, dan supaya download media bisa langsung pakai message asli.
type IncomingMessage struct {
	// Info berisi MessageSource (Chat, Sender, IsGroup, AddressingMode, dll)
	// + ID + PushName + Timestamp. Dipakai sender untuk BuildReaction.
	Info types.MessageInfo

	// Raw adalah *waE2E.Message asli setelah unwrapping ephemeral/view-once
	// oleh whatsmeow. Pegangan ini wajib disimpan untuk download media —
	// client.Download butuh struct media-nya langsung.
	Raw *waE2E.Message

	// Body adalah teks pesan setelah unwrap (Conversation, ExtendedText.Text,
	// atau Caption dari image/video/document). Trimmed.
	Body string

	// RepliedToID kosong kalau pesan ini bukan reply.
	RepliedToID string

	// PushName nama yang ditampilkan oleh WhatsApp untuk pengirim. Kalau
	// kosong, fallback ke nomor pengirim.
	PushName string

	// IsFromMe true kalau pesan dikirim oleh akun bot sendiri (mis. dari
	// device lain). Handler biasanya skip kalau true.
	IsFromMe bool

	// IsGroup true kalau pesan dari grup (chat.Server == @g.us).
	IsGroup bool

	// MediaKind salah satu dari "", "image", "video", "audio", "document",
	// "sticker". Kosong kalau tidak ada media.
	MediaKind string
}

// SenderPhone mengembalikan nomor pengirim tanpa device suffix / server.
// Untuk JID LID, return user-bagian apa adanya — admin matcher pakai
// substring contains, jadi konsisten untuk kedua format.
func (m *IncomingMessage) SenderPhone() string {
	return m.Info.Sender.User
}

// ChatJID JID yang dipakai sebagai tujuan reply. Untuk DM == sender,
// untuk grup == JID grup.
func (m *IncomingMessage) ChatJID() types.JID {
	return m.Info.Chat
}

// IsTextOnly true kalau pesan teks murni (tidak ada media). Beberapa
// handler hanya peduli kalau body cocok, terlepas dari ada-tidaknya media.
func (m *IncomingMessage) IsTextOnly() bool {
	return m.MediaKind == ""
}

// ParseJID wrap types.ParseJID supaya caller di luar package tidak perlu
// import whatsmeow/types langsung. Dipakai mis. notifier email yang nyimpen
// chat ID sebagai string lalu kirim balik via Sender.
func ParseJID(s string) (types.JID, error) {
	return types.ParseJID(s)
}

// fromEvent menormalisasi *events.Message jadi IncomingMessage.
//
// Kembalikan nil kalau event ini bukan pesan yang bisa diproses
// (mis. status broadcast, reaction-only, atau tidak ada body sama sekali
// dan juga tidak ada media). Caller treat nil sebagai "skip".
func fromEvent(evt *events.Message) *IncomingMessage {
	if evt == nil || evt.Message == nil {
		return nil
	}

	body, repliedTo := extractBodyAndReply(evt.Message)
	mediaKind := classifyMedia(evt.Message)

	// Skip kalau tidak ada konten apa pun yang relevan.
	if body == "" && mediaKind == "" {
		return nil
	}

	return &IncomingMessage{
		Info:        evt.Info,
		Raw:         evt.Message,
		Body:        strings.TrimSpace(body),
		RepliedToID: repliedTo,
		PushName:    evt.Info.PushName,
		IsFromMe:    evt.Info.IsFromMe,
		IsGroup:     evt.Info.IsGroup,
		MediaKind:   mediaKind,
	}
}

// extractBodyAndReply ambil teks utama + replied-to ID dari struct waE2E.
// Urutan Conversation, ExtendedTextMessage, lalu caption media.
func extractBodyAndReply(m *waE2E.Message) (body, repliedTo string) {
	if c := m.GetConversation(); c != "" {
		return c, ""
	}
	if ext := m.GetExtendedTextMessage(); ext != nil {
		body = ext.GetText()
		if ctx := ext.GetContextInfo(); ctx != nil {
			repliedTo = ctx.GetStanzaID()
		}
		return body, repliedTo
	}
	// Caption media: Image/Video/Document boleh punya teks.
	if img := m.GetImageMessage(); img != nil {
		if ctx := img.GetContextInfo(); ctx != nil {
			repliedTo = ctx.GetStanzaID()
		}
		return img.GetCaption(), repliedTo
	}
	if vid := m.GetVideoMessage(); vid != nil {
		if ctx := vid.GetContextInfo(); ctx != nil {
			repliedTo = ctx.GetStanzaID()
		}
		return vid.GetCaption(), repliedTo
	}
	if doc := m.GetDocumentMessage(); doc != nil {
		if ctx := doc.GetContextInfo(); ctx != nil {
			repliedTo = ctx.GetStanzaID()
		}
		return doc.GetCaption(), repliedTo
	}
	return "", ""
}

// classifyMedia return tipe media kalau ada, "" kalau tidak.
// Order penting — image dicek dulu karena banyak DTO punya field redundant.
func classifyMedia(m *waE2E.Message) string {
	switch {
	case m.GetImageMessage() != nil:
		return "image"
	case m.GetVideoMessage() != nil:
		return "video"
	case m.GetAudioMessage() != nil:
		return "audio"
	case m.GetDocumentMessage() != nil:
		return "document"
	case m.GetStickerMessage() != nil:
		return "sticker"
	}
	return ""
}
