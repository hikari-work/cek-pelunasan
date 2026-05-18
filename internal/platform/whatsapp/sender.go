// Package whatsapp menyediakan integrasi WhatsApp via gateway HTTP eksternal.
//
// Layer ini terdiri dari:
//   - Sender: HTTP client tipis ke gateway (POST /send/message, /send/file, dst)
//   - Webhook: endpoint yang menerima pesan masuk dari gateway
//   - Router: dispatch pesan ke handler berdasarkan prefix command
//
// Handler per fitur (pelunasan calculator, hotkolek, dll) belum semua diport —
// router fallback ke pendingHandler supaya gateway tetap dapat 200 OK.
package whatsapp

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math/rand"
	"mime/multipart"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Sender bicara HTTP ke gateway WhatsApp. Auth via Basic kalau username/password
// di-config; selain itu request dikirim tanpa auth.
type Sender struct {
	BaseURL  string
	Username string
	Password string
	HTTP     *http.Client
}

func NewSender(baseURL, username, password string) *Sender {
	return &Sender{
		BaseURL:  strings.TrimRight(baseURL, "/"),
		Username: username,
		Password: password,
		HTTP:     &http.Client{Timeout: 30 * time.Second},
	}
}

// SendText kirim pesan teks. replyTo boleh "" kalau bukan reply.
func (s *Sender) SendText(ctx context.Context, phone, message, replyTo string) error {
	body := map[string]string{"phone": phone, "message": message}
	if replyTo != "" {
		body["reply_message_id"] = replyTo
	}
	return s.postJSON(ctx, "/send/message", body)
}

// UpdateMessage edit pesan existing.
func (s *Sender) UpdateMessage(ctx context.Context, phone, messageID, newText string) error {
	body := map[string]string{"phone": phone, "message": newText, "message_id": messageID}
	return s.postJSON(ctx, "/message/"+url.PathEscape(messageID)+"/update", body)
}

// SendReaction kirim emoji random sebagai feedback "sedang diproses".
func (s *Sender) SendReaction(ctx context.Context, phone, messageID string) error {
	emojis := []string{"👌", "✍", "🙏", "👍", "🤝", "👊"}
	body := map[string]string{
		"phone":      phone,
		"message_id": messageID,
		"emoji":      emojis[rand.Intn(len(emojis))],
	}
	return s.postJSON(ctx, "/message/"+url.PathEscape(messageID)+"/reaction", body)
}

// SendFile upload file via multipart.
func (s *Sender) SendFile(ctx context.Context, phone string, fileBytes []byte, fileName, caption string) error {
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	_ = mw.WriteField("phone", phone)
	_ = mw.WriteField("caption", caption)
	part, err := mw.CreateFormFile("file", fileName)
	if err != nil {
		return err
	}
	if _, err := part.Write(fileBytes); err != nil {
		return err
	}
	if err := mw.Close(); err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.BaseURL+"/send/file", &buf)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", mw.FormDataContentType())
	if s.Username != "" {
		req.SetBasicAuth(s.Username, s.Password)
	}
	return s.do(req)
}

func (s *Sender) postJSON(ctx context.Context, path string, body any) error {
	data, err := json.Marshal(body)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.BaseURL+path, bytes.NewReader(data))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if s.Username != "" {
		req.SetBasicAuth(s.Username, s.Password)
	}
	return s.do(req)
}

func (s *Sender) do(req *http.Request) error {
	resp, err := s.HTTP.Do(req)
	if err != nil {
		return fmt.Errorf("whatsapp gateway: %w", err)
	}
	defer func(Body io.ReadCloser) {
		_ = Body.Close()
	}(resp.Body)
	if resp.StatusCode >= 400 {
		buf, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		slog.Warn("whatsapp gateway non-2xx",
			"path", req.URL.Path, "status", resp.StatusCode, "body", string(buf))
		return fmt.Errorf("status %d", resp.StatusCode)
	}
	_, _ = io.Copy(io.Discard, resp.Body)
	return nil
}
