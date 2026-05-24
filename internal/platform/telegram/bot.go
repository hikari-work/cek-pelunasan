// Package telegram membungkus go-telegram-bot-api dan menyediakan kontrak
// CommandHandler, CallbackHandler, plus router yang men-dispatch update
// ke handler berdasarkan command (kata pertama) atau callback prefix
// (token pertama dipisahkan "_").
package telegram

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
)

// Bot membungkus tgbotapi.BotAPI plus context cancellation dan adaptasi
// kebiasaan service (parseMode default Markdown, helper kirim file dari bytes).
type Bot struct {
	API     *tgbotapi.BotAPI
	OwnerID int64

	authed *auth.AuthorizedChats
}

func NewBot(api *tgbotapi.BotAPI, owner int64, authed *auth.AuthorizedChats) *Bot {
	return &Bot{API: api, OwnerID: owner, authed: authed}
}

// SendText kirim teks Markdown. Mengembalikan messageID yang dikirim.
func (b *Bot) SendText(chatID int64, text string) (int, error) {
	msg := tgbotapi.NewMessage(chatID, text)
	msg.ParseMode = tgbotapi.ModeMarkdown
	sent, err := b.API.Send(msg)
	if err != nil {
		return 0, err
	}
	return sent.MessageID, nil
}

// SendTextWithKeyboard kirim teks + inline keyboard.
func (b *Bot) SendTextWithKeyboard(chatID int64, text string, kb tgbotapi.InlineKeyboardMarkup) (int, error) {
	msg := tgbotapi.NewMessage(chatID, text)
	msg.ParseMode = tgbotapi.ModeMarkdown
	msg.ReplyMarkup = kb
	sent, err := b.API.Send(msg)
	if err != nil {
		return 0, err
	}
	return sent.MessageID, nil
}

// EditText edit teks pesan yang sudah ada.
func (b *Bot) EditText(chatID int64, messageID int, text string) error {
	edit := tgbotapi.NewEditMessageText(chatID, messageID, text)
	edit.ParseMode = tgbotapi.ModeMarkdown
	_, err := b.API.Send(edit)
	return err
}

// EditTextWithMarkup edit teks + inline keyboard sekaligus.
func (b *Bot) EditTextWithMarkup(chatID int64, messageID int, text string, kb tgbotapi.InlineKeyboardMarkup) error {
	edit := tgbotapi.NewEditMessageTextAndMarkup(chatID, messageID, text, kb)
	edit.ParseMode = tgbotapi.ModeMarkdown
	_, err := b.API.Send(edit)
	return err
}

// SendDocument kirim file dari bytes (tanpa simpan ke disk dulu).
func (b *Bot) SendDocument(chatID int64, fileName string, data []byte) error {
	_, err := b.SendDocumentWithID(chatID, fileName, data)
	return err
}

// SendDocumentWithID kirim file dari bytes dan kembalikan messageID.
func (b *Bot) SendDocumentWithID(chatID int64, fileName string, data []byte) (int, error) {
	doc := tgbotapi.NewDocument(chatID, tgbotapi.FileBytes{Name: fileName, Bytes: data})
	sent, err := b.API.Send(doc)
	if err != nil {
		return 0, err
	}
	return sent.MessageID, nil
}

// DeleteMessageDelayed hapus pesan setelah delay tertentu.
// Non-blocking: langsung return setelah spawn goroutine.
func (b *Bot) DeleteMessageDelayed(chatID int64, messageID int, delay time.Duration) {
	go func() {
		time.Sleep(delay)
		_ = b.DeleteMessage(chatID, messageID)
	}()
}

// DownloadFile resolve fileID ke direct URL lalu unduh isinya. Hati-hati: Telegram
// hanya mengizinkan download untuk file < 20MB lewat Bot API. File yang lebih besar
// akan return error.
func (b *Bot) DownloadFile(fileID string) ([]byte, error) {
	url, err := b.API.GetFileDirectURL(fileID)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() {
		if closeErr := resp.Body.Close(); closeErr != nil {
			slog.Error("failed to close response body", "error", closeErr)
		}
	}()
	if resp.StatusCode/100 != 2 {
		return nil, fmt.Errorf("download file: %s", resp.Status)
	}
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	return data, nil
}

// AnswerCallback merespons callback query (hilangkan loading spinner di tombol).
func (b *Bot) AnswerCallback(callbackID string, text string) error {
	_, err := b.API.Request(tgbotapi.NewCallback(callbackID, text))
	return err
}

// DeleteMessage hapus pesan.
func (b *Bot) DeleteMessage(chatID int64, messageID int) error {
	_, err := b.API.Request(tgbotapi.NewDeleteMessage(chatID, messageID))
	return err
}

// CommandHandler menangani satu command bot (mis. /tagih).
type CommandHandler interface {
	Command() string
	Description() string
	Handle(ctx context.Context, b *Bot, msg *tgbotapi.Message)
}

// DocumentHandler menangani pesan dengan attachment Document. Dipanggil oleh
// router untuk update message yang berisi Message.Document (bukan Text).
// Auth/role check tanggung jawab handler sendiri.
type DocumentHandler interface {
	HandleDocument(ctx context.Context, b *Bot, msg *tgbotapi.Message)
}

// CallbackHandler menangani callback inline button berdasarkan prefix.
// Prefix dipisahkan dari payload pakai "_" (ikut konvensi legacy).
type CallbackHandler interface {
	Prefix() string
	Handle(ctx context.Context, b *Bot, q *tgbotapi.CallbackQuery)
}

// MiddlewareFunc dijalankan sebelum handler. Kembalikan false untuk menolak
// request (tidak dispatch ke handler).
type MiddlewareFunc func(ctx context.Context, b *Bot, chatID int64) bool

// Router mendispatch update ke handler. Aman dipakai concurrent — sync.RWMutex
// melindungi map handler kalau perlu register dynamic, tapi normalnya semua
// handler register sekali saat startup.
type Router struct {
	mu         sync.RWMutex
	commands   map[string]CommandHandler
	callbacks  map[string]CallbackHandler
	documents  []DocumentHandler
	commandMW  []MiddlewareFunc
	callbackMW []MiddlewareFunc
}

func NewRouter() *Router {
	return &Router{
		commands:  make(map[string]CommandHandler),
		callbacks: make(map[string]CallbackHandler),
	}
}

func (r *Router) RegisterCommand(h CommandHandler) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.commands[h.Command()] = h
}

// RegisterDocumentHandler register handler untuk pesan dengan attachment Document.
// Semua handler dipanggil berurutan (legacy: hanya satu handler — admin upload SLIK,
// tapi pakai slice supaya extensible).
func (r *Router) RegisterDocumentHandler(h DocumentHandler) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.documents = append(r.documents, h)
}

// Commands snapshot semua command handler yang terdaftar — dipakai untuk
// SetMyCommands ke Telegram.
func (r *Router) Commands() []CommandHandler {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]CommandHandler, 0, len(r.commands))
	for _, h := range r.commands {
		out = append(out, h)
	}
	return out
}

func (r *Router) RegisterCallback(h CallbackHandler) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.callbacks[h.Prefix()] = h
}

func (r *Router) UseCommand(mw MiddlewareFunc)  { r.commandMW = append(r.commandMW, mw) }
func (r *Router) UseCallback(mw MiddlewareFunc) { r.callbackMW = append(r.callbackMW, mw) }

// dispatchMessage dipanggil dari Run untuk tiap message text.
func (r *Router) dispatchMessage(ctx context.Context, b *Bot, msg *tgbotapi.Message) {
	if msg == nil {
		return
	}
	// Pesan dengan attachment dokumen → dispatch ke document handler dulu
	// (handler bertanggung jawab cek role & ekstensi). Kalau msg juga punya
	// Text/Caption yang dimulai dengan "/", text command tetap dijalankan.
	if msg.Document != nil {
		r.mu.RLock()
		docs := append([]DocumentHandler(nil), r.documents...)
		r.mu.RUnlock()
		for _, h := range docs {
			h.HandleDocument(ctx, b, msg)
		}
		// Tidak return — supaya kalau ada caption "/cmd" tetap dipanggil.
	}
	if msg.Text == "" {
		return
	}
	cmd := strings.SplitN(msg.Text, " ", 2)[0]
	r.mu.RLock()
	h, ok := r.commands[cmd]
	if !ok {
		// fallback ke /id (legacy convention)
		h, ok = r.commands["/id"]
	}
	r.mu.RUnlock()
	if !ok {
		return
	}
	for _, mw := range r.commandMW {
		if !mw(ctx, b, msg.Chat.ID) {
			return
		}
	}
	h.Handle(ctx, b, msg)
}

// dispatchCallback dipanggil dari Run untuk tiap callback query.
func (r *Router) dispatchCallback(ctx context.Context, b *Bot, q *tgbotapi.CallbackQuery) {
	if q == nil || q.Data == "" {
		return
	}
	prefix := strings.SplitN(q.Data, "_", 2)[0]
	r.mu.RLock()
	h, ok := r.callbacks[prefix]
	if !ok {
		h, ok = r.callbacks["none"]
	}
	r.mu.RUnlock()
	if !ok {
		return
	}
	for _, mw := range r.callbackMW {
		if !mw(ctx, b, q.From.ID) {
			return
		}
	}
	h.Handle(ctx, b, q)
}
