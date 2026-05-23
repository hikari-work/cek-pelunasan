package whatsapp

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"mime"
	"path/filepath"
	"strings"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"google.golang.org/protobuf/proto"
)

// Sender adalah lapisan tipis di atas *whatsmeow.Client untuk operasi yang
// dipakai handler bisnis: SendText / Edit / Reaction / SendDocument /
// DownloadMedia. Semua method idempoten dari sisi caller — sender tidak
// menyimpan state apa pun, aman dipakai concurrent.
type Sender struct {
	cli *whatsmeow.Client
}

// NewSender bungkus *whatsmeow.Client. Mengembalikan nil kalau cli nil
// supaya panggilan berikutnya jadi no-op (lihat method receiver guard).
func NewSender(cli *whatsmeow.Client) *Sender {
	if cli == nil {
		return nil
	}
	return &Sender{cli: cli}
}

// reactionEmojis adalah daftar emoji yang dipakai untuk reaksi "sedang
// memproses". Sama dengan legacy supaya UX tidak berubah.
var reactionEmojis = []string{"👌", "✍", "🙏", "👍", "🤝", "👊"}

// SendText kirim pesan teks biasa. Reply optional via msgInfo — kalau nil,
// kirim sebagai pesan baru. Mengembalikan ID pesan yang baru dikirim
// (kosong saat error).
func (s *Sender) SendText(ctx context.Context, to types.JID, text string, replyTo *types.MessageInfo) (string, error) {
	if s == nil || s.cli == nil {
		return "", errors.New("whatsapp sender: client nil")
	}
	msg := &waE2E.Message{
		Conversation: proto.String(text),
	}
	if replyTo != nil {
		// ContextInfo memerlukan ExtendedTextMessage, bukan Conversation polos.
		msg = &waE2E.Message{
			ExtendedTextMessage: &waE2E.ExtendedTextMessage{
				Text:        proto.String(text),
				ContextInfo: buildReplyContext(replyTo),
			},
		}
	}
	resp, err := s.cli.SendMessage(ctx, to, msg)
	if err != nil {
		return "", fmt.Errorf("send text: %w", err)
	}
	return resp.ID, nil
}

// EditMessage edit pesan yang sebelumnya dikirim oleh kita. Tidak bisa
// edit pesan orang lain — WhatsApp akan tolak. msgID harus ID pesan
// outbound yang masih dalam jendela 15 menit (limitasi WhatsApp).
func (s *Sender) EditMessage(ctx context.Context, chat types.JID, msgID string, newText string) error {
	if s == nil || s.cli == nil {
		return errors.New("whatsapp sender: client nil")
	}
	newContent := &waE2E.Message{
		Conversation: proto.String(newText),
	}
	edit := s.cli.BuildEdit(chat, msgID, newContent)
	if _, err := s.cli.SendMessage(ctx, chat, edit); err != nil {
		return fmt.Errorf("edit message: %w", err)
	}
	return nil
}

// React kirim reaksi emoji ke pesan. Emoji diambil random dari
// reactionEmojis. Caller boleh pass IncomingMessage.Info langsung —
// chat, sender, dan ID semua diambil dari sana.
func (s *Sender) React(ctx context.Context, info types.MessageInfo) error {
	return s.ReactWith(ctx, info, reactionEmojis[rand.Intn(len(reactionEmojis))])
}

// ReactWith varian React dengan emoji eksplisit.
// Pass emoji "" untuk menghapus reaksi yang sudah ada.
func (s *Sender) ReactWith(ctx context.Context, info types.MessageInfo, emoji string) error {
	if s == nil || s.cli == nil {
		return errors.New("whatsapp sender: client nil")
	}
	react := s.cli.BuildReaction(info.Chat, info.Sender, info.ID, emoji)
	if _, err := s.cli.SendMessage(ctx, info.Chat, react); err != nil {
		return fmt.Errorf("send reaction: %w", err)
	}
	return nil
}

// SendDocument upload + kirim file sebagai DocumentMessage. Cocok untuk
// PDF, dokumen Excel, dll. caption boleh kosong.
//
// Ukuran file praktis dibatasi sekitar 100MB oleh WhatsApp; di atas itu
// upload tetap jalan tapi delivery sering gagal di sisi penerima.
func (s *Sender) SendDocument(ctx context.Context, to types.JID, data []byte, filename, caption string) error {
	if s == nil || s.cli == nil {
		return errors.New("whatsapp sender: client nil")
	}
	if len(data) == 0 {
		return errors.New("whatsapp sender: empty document")
	}
	uploaded, err := s.cli.Upload(ctx, data, whatsmeow.MediaDocument)
	if err != nil {
		return fmt.Errorf("upload document: %w", err)
	}
	mimeType := detectMime(filename, data)
	doc := &waE2E.Message{
		DocumentMessage: &waE2E.DocumentMessage{
			URL:           proto.String(uploaded.URL),
			DirectPath:    proto.String(uploaded.DirectPath),
			MediaKey:      uploaded.MediaKey,
			Mimetype:      proto.String(mimeType),
			FileEncSHA256: uploaded.FileEncSHA256,
			FileSHA256:    uploaded.FileSHA256,
			FileLength:    proto.Uint64(uploaded.FileLength),
			FileName:      proto.String(filename),
			Caption:       proto.String(caption),
		},
	}
	if _, err := s.cli.SendMessage(ctx, to, doc); err != nil {
		return fmt.Errorf("send document: %w", err)
	}
	return nil
}

// DownloadMedia ambil bytes media dari pesan inbound.
// msg adalah waE2E.Message dari IncomingMessage.Raw — method ini akan pilih
// tipe media yang tepat (image/document/video/audio/sticker) dan download.
func (s *Sender) DownloadMedia(ctx context.Context, msg *waE2E.Message) ([]byte, error) {
	if s == nil || s.cli == nil {
		return nil, errors.New("whatsapp sender: client nil")
	}
	if msg == nil {
		return nil, errors.New("whatsapp sender: message nil")
	}

	// Find the first non-nil downloadable message type
	var downloadable whatsmeow.DownloadableMessage
	switch {
	case msg.GetImageMessage() != nil:
		downloadable = msg.GetImageMessage()
	case msg.GetDocumentMessage() != nil:
		downloadable = msg.GetDocumentMessage()
	case msg.GetVideoMessage() != nil:
		downloadable = msg.GetVideoMessage()
	case msg.GetAudioMessage() != nil:
		downloadable = msg.GetAudioMessage()
	case msg.GetStickerMessage() != nil:
		downloadable = msg.GetStickerMessage()
	default:
		return nil, errors.New("no downloadable media found in message")
	}

	data, err := s.cli.Download(ctx, downloadable)
	if err != nil {
		return nil, fmt.Errorf("download media: %w", err)
	}
	return data, nil
}

// buildReplyContext bangun ContextInfo untuk reply ke pesan tertentu.
// QuotedMessage di-set ke konten kosong; WhatsApp client akan render
// quote berdasarkan StanzaID + Participant saja.
func buildReplyContext(target *types.MessageInfo) *waE2E.ContextInfo {
	if target == nil {
		return nil
	}
	return &waE2E.ContextInfo{
		StanzaID:    proto.String(target.ID),
		Participant: proto.String(target.Sender.ToNonAD().String()),
		QuotedMessage: &waE2E.Message{
			Conversation: proto.String(""),
		},
	}
}

// detectMime tebak content-type dari ekstensi filename. Fallback ke
// application/octet-stream supaya WhatsApp tetap mau mengirim sebagai
// dokumen generic.
func detectMime(filename string, _ []byte) string {
	ext := strings.ToLower(filepath.Ext(filename))
	if ext == "" {
		return "application/octet-stream"
	}
	if mt := mime.TypeByExtension(ext); mt != "" {
		return mt
	}
	switch ext {
	case ".pdf":
		return "application/pdf"
	case ".csv":
		return "text/csv"
	case ".xlsx":
		return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
	}
	return "application/octet-stream"
}
