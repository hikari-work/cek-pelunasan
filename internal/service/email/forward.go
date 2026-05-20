package email

import (
	"context"
	"fmt"
	"log/slog"
	"strings"
	"time"
)

// Notifier panggil-balik untuk kirim pesan ke chat WhatsApp pengirim.
// Diisolasi via interface supaya forward service tidak depend ke whatsapp
// package langsung — dan supaya gampang di-stub di test.
type Notifier interface {
	NotifyText(ctx context.Context, chatID, message string)
}

// Forwarder orkestrasi end-to-end: ambil session siap-kirim → bangun email
// → kirim via SMTP → notify pengirim. Aman dipakai concurrent.
type Forwarder struct {
	Sender   *Sender
	From     string
	Notifier Notifier
}

func NewForwarder(sender *Sender, from string, notifier Notifier) *Forwarder {
	return &Forwarder{Sender: sender, From: from, Notifier: notifier}
}

// Send kirim email berisi semua media dari sesi. Dipanggil saat user .done
// atau saat TTL session habis. Method ini blocking tapi caller diharapkan
// memanggil dari goroutine (auto-send timer sudah jalan di goroutine).
//
// Notifikasi WhatsApp dikirim sebagai konfirmasi sukses/gagal — kalau
// notifier nil, hanya log.
func (f *Forwarder) Send(ctx context.Context, s *Session) {
	if f == nil || s == nil {
		return
	}

	media := s.Media()
	if len(media) == 0 {
		f.notify(ctx, s, "❌ Tidak ada media yang terkumpul. Email tidak dikirim.")
		return
	}

	atts := make([]Attachment, 0, len(media))
	skipped := 0
	for _, m := range media {
		if len(m.Bytes) == 0 {
			skipped++
			continue
		}
		atts = append(atts, Attachment{
			Filename:    m.Filename,
			Bytes:       m.Bytes,
			ContentType: defaultMimeType(m.MediaType),
		})
	}
	if len(atts) == 0 {
		f.notify(ctx, s, "❌ Semua media gagal didownload. Email tidak dikirim.")
		return
	}

	mail := Mail{
		From:        f.From,
		To:          s.Recipient,
		Subject:     buildSubject(s),
		Body:        buildBody(s, media),
		Attachments: atts,
	}
	if err := f.Sender.Send(ctx, mail); err != nil {
		slog.Error("email forward: kirim gagal",
			"phone", s.SenderPhone, "to", s.Recipient, "err", err)
		f.notify(ctx, s, "❌ Gagal mengirim email. Silakan coba lagi.")
		return
	}

	slog.Info("email forward: terkirim",
		"phone", s.SenderPhone, "to", s.Recipient,
		"attachments", len(atts), "total", len(media), "skipped", skipped)
	f.notify(ctx, s, fmt.Sprintf("✅ Email berhasil dikirim ke %s\n📎 %d dari %d file terkirim.",
		s.Recipient, len(atts), len(media)))
}

func (f *Forwarder) notify(ctx context.Context, s *Session, msg string) {
	if f.Notifier == nil {
		return
	}
	f.Notifier.NotifyText(ctx, s.ChatID, msg)
}

func buildSubject(s *Session) string {
	wib := time.FixedZone("WIB", 7*3600)
	return fmt.Sprintf("WA Forward dari %s — %s",
		s.FromName, time.Now().In(wib).Format("02/01/2006 15:04"))
}

func buildBody(s *Session, media []CollectedMedia) string {
	var b strings.Builder
	b.WriteString("Pesan diteruskan dari WhatsApp\n\n")
	fmt.Fprintf(&b, "Pengirim : %s\n", s.FromName)
	fmt.Fprintf(&b, "Nomor    : %s\n\n", s.SenderPhone)

	if len(media) > 0 {
		b.WriteString("Media yang dilampirkan:\n")
		for i, m := range media {
			fmt.Fprintf(&b, "%d. %s", i+1, m.Filename)
			if strings.TrimSpace(m.Caption) != "" {
				fmt.Fprintf(&b, " — %s", m.Caption)
			}
			b.WriteString("\n")
		}
	}
	return b.String()
}

func defaultMimeType(mediaType string) string {
	switch mediaType {
	case "image":
		return "image/jpeg"
	case "video":
		return "video/mp4"
	case "audio":
		return "audio/ogg"
	case "document":
		return "application/octet-stream"
	}
	return "application/octet-stream"
}
