package email

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/base64"
	"errors"
	"fmt"
	"mime"
	"net"
	"net/smtp"
	"path/filepath"
	"strings"
	"time"
)

// SMTPConfig parameter koneksi SMTP. UseSSL=true → implicit TLS (port 465).
// UseSSL=false → STARTTLS (port 587). Plain SMTP tidak didukung — semua
// transport selalu encrypted.
type SMTPConfig struct {
	Host     string
	Port     int
	Username string
	Password string
	UseSSL   bool
}

// Mail satu email yang siap dikirim.
type Mail struct {
	From        string
	To          string
	Subject     string
	Body        string
	Attachments []Attachment
}

// Attachment file dilampirkan di email. ContentType boleh kosong — akan
// di-detect dari ekstensi Filename.
type Attachment struct {
	Filename    string
	ContentType string
	Bytes       []byte
}

// Sender SMTP client tipis. Aman dipakai concurrent — tidak menyimpan koneksi.
type Sender struct {
	cfg SMTPConfig
}

func NewSender(cfg SMTPConfig) *Sender {
	return &Sender{cfg: cfg}
}

// Send kirim satu email. Timeout dihormati lewat ctx untuk koneksi awal;
// SMTP transaction sendiri tidak di-cancel mid-flight (smtp library stdlib
// tidak expose context).
func (s *Sender) Send(ctx context.Context, m Mail) error {
	if s == nil {
		return errors.New("email sender: nil receiver")
	}
	if strings.TrimSpace(s.cfg.Host) == "" {
		return errors.New("email sender: SMTP host kosong")
	}
	if strings.TrimSpace(m.From) == "" {
		return errors.New("email sender: From kosong")
	}
	if strings.TrimSpace(m.To) == "" {
		return errors.New("email sender: To kosong")
	}

	addr := fmt.Sprintf("%s:%d", s.cfg.Host, s.cfg.Port)
	body, err := buildMessage(m)
	if err != nil {
		return fmt.Errorf("build message: %w", err)
	}

	dialer := &net.Dialer{Timeout: 30 * time.Second}
	tlsCfg := &tls.Config{ServerName: s.cfg.Host, MinVersion: tls.VersionTLS12}

	var conn net.Conn
	if s.cfg.UseSSL {
		conn, err = tls.DialWithDialer(dialer, "tcp", addr, tlsCfg)
	} else {
		conn, err = dialer.DialContext(ctx, "tcp", addr)
	}
	if err != nil {
		return fmt.Errorf("dial smtp: %w", err)
	}
	defer conn.Close()

	c, err := smtp.NewClient(conn, s.cfg.Host)
	if err != nil {
		return fmt.Errorf("new smtp client: %w", err)
	}
	defer func() { _ = c.Close() }()

	if !s.cfg.UseSSL {
		if ok, _ := c.Extension("STARTTLS"); ok {
			if err := c.StartTLS(tlsCfg); err != nil {
				return fmt.Errorf("starttls: %w", err)
			}
		}
	}

	if s.cfg.Username != "" {
		auth := smtp.PlainAuth("", s.cfg.Username, s.cfg.Password, s.cfg.Host)
		if err := c.Auth(auth); err != nil {
			return fmt.Errorf("smtp auth: %w", err)
		}
	}

	if err := c.Mail(m.From); err != nil {
		return fmt.Errorf("MAIL FROM: %w", err)
	}
	if err := c.Rcpt(m.To); err != nil {
		return fmt.Errorf("RCPT TO: %w", err)
	}
	w, err := c.Data()
	if err != nil {
		return fmt.Errorf("DATA: %w", err)
	}
	if _, err := w.Write(body); err != nil {
		return fmt.Errorf("write body: %w", err)
	}
	if err := w.Close(); err != nil {
		return fmt.Errorf("close DATA: %w", err)
	}
	return c.Quit()
}

// buildMessage rakit MIME multipart/mixed: header → text/plain body →
// satu part per attachment dengan base64 encoding.
//
// CRLF dipakai untuk semua line terminator sesuai RFC 5322.
func buildMessage(m Mail) ([]byte, error) {
	boundary, err := newBoundary()
	if err != nil {
		return nil, err
	}

	var buf bytes.Buffer
	header := func(k, v string) { fmt.Fprintf(&buf, "%s: %s\r\n", k, v) }
	header("From", m.From)
	header("To", m.To)
	header("Subject", encodeHeader(m.Subject))
	header("MIME-Version", "1.0")
	header("Date", time.Now().UTC().Format(time.RFC1123Z))

	if len(m.Attachments) == 0 {
		header("Content-Type", "text/plain; charset=UTF-8")
		header("Content-Transfer-Encoding", "8bit")
		buf.WriteString("\r\n")
		buf.WriteString(m.Body)
		return buf.Bytes(), nil
	}

	header("Content-Type", `multipart/mixed; boundary="`+boundary+`"`)
	buf.WriteString("\r\n")

	// Body part.
	fmt.Fprintf(&buf, "--%s\r\n", boundary)
	buf.WriteString("Content-Type: text/plain; charset=UTF-8\r\n")
	buf.WriteString("Content-Transfer-Encoding: 8bit\r\n\r\n")
	buf.WriteString(m.Body)
	buf.WriteString("\r\n")

	// Attachments.
	for _, att := range m.Attachments {
		ct := att.ContentType
		if strings.TrimSpace(ct) == "" {
			ct = detectContentType(att.Filename)
		}
		fmt.Fprintf(&buf, "--%s\r\n", boundary)
		fmt.Fprintf(&buf, "Content-Type: %s; name=\"%s\"\r\n", ct, att.Filename)
		buf.WriteString("Content-Transfer-Encoding: base64\r\n")
		fmt.Fprintf(&buf, "Content-Disposition: attachment; filename=\"%s\"\r\n\r\n",
			att.Filename)
		writeBase64Wrapped(&buf, att.Bytes)
		buf.WriteString("\r\n")
	}
	fmt.Fprintf(&buf, "--%s--\r\n", boundary)
	return buf.Bytes(), nil
}

// writeBase64Wrapped base64 encode + wrap per 76 char (RFC 2045).
func writeBase64Wrapped(buf *bytes.Buffer, data []byte) {
	enc := base64.StdEncoding.EncodeToString(data)
	const lineLen = 76
	for i := 0; i < len(enc); i += lineLen {
		end := i + lineLen
		if end > len(enc) {
			end = len(enc)
		}
		buf.WriteString(enc[i:end])
		buf.WriteString("\r\n")
	}
}

// encodeHeader RFC 2047 encoded-word kalau header mengandung non-ASCII,
// else apa adanya. mime.QEncoding default, fallback ke string original
// kalau encoding tidak diperlukan.
func encodeHeader(s string) string {
	for _, r := range s {
		if r > 127 {
			return mime.QEncoding.Encode("UTF-8", s)
		}
	}
	return s
}

func detectContentType(filename string) string {
	ext := strings.ToLower(filepath.Ext(filename))
	if ext == "" {
		return "application/octet-stream"
	}
	if mt := mime.TypeByExtension(ext); mt != "" {
		return mt
	}
	return "application/octet-stream"
}

func newBoundary() (string, error) {
	var b [12]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return "boundary-" + base64.RawURLEncoding.EncodeToString(b[:]), nil
}
