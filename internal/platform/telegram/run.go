package telegram

import (
	"context"
	"log/slog"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
)

// RegisterBotCommands kirim SetMyCommands ke Telegram supaya menu autocomplete
// muncul saat user ngetik "/". Hanya command dengan Description() non-empty
// yang ikut didaftarkan (ikuti konvensi legacy).
func RegisterBotCommands(api *tgbotapi.BotAPI, handlers []CommandHandler) error {
	cmds := make([]tgbotapi.BotCommand, 0, len(handlers))
	for _, h := range handlers {
		desc := strings.TrimSpace(h.Description())
		if desc == "" {
			continue
		}
		cmds = append(cmds, tgbotapi.BotCommand{
			Command:     strings.TrimPrefix(h.Command(), "/"),
			Description: desc,
		})
	}
	if len(cmds) == 0 {
		slog.Warn("no bot commands to register")
		return nil
	}
	cfg := tgbotapi.NewSetMyCommands(cmds...)
	_, err := api.Request(cfg)
	if err != nil {
		return err
	}
	slog.Info("registered bot commands to telegram", "count", len(cmds))
	return nil
}

// Run consume update dari long-polling sampai ctx di-cancel.
// Tiap update di-dispatch di goroutine terpisah (legacy juga @Async),
// tapi panic di handler diisolasi pakai recover().
func Run(ctx context.Context, b *Bot, r *Router) error {
	uc := tgbotapi.NewUpdate(0)
	uc.Timeout = 30
	updates := b.API.GetUpdatesChan(uc)
	defer b.API.StopReceivingUpdates()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case update, ok := <-updates:
			if !ok {
				return nil
			}
			go func(u tgbotapi.Update) {
				defer func() {
					if rec := recover(); rec != nil {
						slog.Error("panic in update handler", "panic", rec)
					}
				}()
				switch {
				case u.Message != nil:
					r.dispatchMessage(ctx, b, u.Message)
				case u.CallbackQuery != nil:
					// answer callback dulu (hilangkan loading spinner) — best effort.
					_ = b.AnswerCallback(u.CallbackQuery.ID, "")
					r.dispatchCallback(ctx, b, u.CallbackQuery)
				}
			}(update)
		}
	}
}
