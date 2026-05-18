// Package telegram membungkus go-telegram-bot-api dan menyediakan kontrak
// CommandHandler, CallbackHandler, plus router yang men-dispatch update
// ke handler berdasarkan command (kata pertama) atau callback prefix
// (token pertama dipisahkan "_").
package telegram

import (
	"context"
	"strings"
	"sync"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
)

// Bot membungkus tgbotapi.BotAPI plus context cancellation dan adaptasi
// kebiasaan service (parseMode default Markdown, helper kirim file dari bytes).
type Bot struct {
	API   *tgbotapi.BotAPI
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
	doc := tgbotapi.NewDocument(chatID, tgbotapi.FileBytes{Name: fileName, Bytes: data})
	_, err := b.API.Send(doc)
	return err
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
	mu          sync.RWMutex
	commands    map[string]CommandHandler
	callbacks   map[string]CallbackHandler
	commandMW   []MiddlewareFunc
	callbackMW  []MiddlewareFunc
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
	if msg == nil || msg.Text == "" {
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

// AuthMiddleware tolak chatID yang tidak terdaftar di AuthorizedChats.
// Kirim pesan penolakan ringan biar user tahu.
func AuthMiddleware(authed *auth.AuthorizedChats) MiddlewareFunc {
	return func(ctx context.Context, b *Bot, chatID int64) bool {
		if !authed.IsAuthorized(chatID) {
			_, _ = b.SendText(chatID, "Anda tidak memiliki akses ke bot ini")
			return false
		}
		return true
	}
}

// RoleMiddleware tolak kalau role pengguna tidak masuk daftar allowed.
// ADMIN selalu lolos. Kalau allowed kosong, hanya cek "sudah authorized".
func RoleMiddleware(authed *auth.AuthorizedChats, allowed ...entity.Role) MiddlewareFunc {
	return func(ctx context.Context, b *Bot, chatID int64) bool {
		if !authed.IsAuthorized(chatID) {
			_, _ = b.SendText(chatID, "Anda tidak memiliki akses ke bot ini")
			return false
		}
		role, err := authed.Roles(ctx, chatID)
		if err != nil {
			_, _ = b.SendText(chatID, "Gagal memeriksa role.")
			return false
		}
		if role == entity.RoleAdmin {
			return true
		}
		if len(allowed) == 0 {
			return true
		}
		for _, r := range allowed {
			if role == r {
				return true
			}
		}
		_, _ = b.SendText(chatID, "Anda tidak memiliki akses ke bot ini")
		return false
	}
}
