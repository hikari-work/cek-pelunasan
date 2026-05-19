// Package sliknotifier scan R2 PDF folder secara berkala dan kirim notifikasi
// Telegram ke AO yang user_code-nya cocok dengan prefix file. State persisten
// di MongoDB supaya restart tidak bikin notifikasi dobel.
package sliknotifier

import (
	"context"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/r2"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
)

// Notifier scan & notify. Aman dipanggil concurrent — tiap Run() membuat snapshot
// listing R2 sendiri. Interval 60s sesuai legacy.
type Notifier struct {
	Storage  *r2.Client
	Bot      *telegram.Bot
	Users    *repository.UserRepo
	Notified *repository.SlikNotifiedFileRepo
	Interval time.Duration
}

// Run loop sampai ctx dibatalkan.
func (n *Notifier) Run(ctx context.Context) {
	if n.Storage == nil || n.Bot == nil || n.Users == nil || n.Notified == nil {
		slog.Warn("slik notifier not fully wired; loop disabled")
		return
	}
	interval := n.Interval
	if interval <= 0 {
		interval = 60 * time.Second
	}
	t := time.NewTicker(interval)
	defer t.Stop()

	// First tick segera supaya tidak nunggu interval pertama.
	n.tick(ctx)
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			n.tick(ctx)
		}
	}
}

func (n *Notifier) tick(ctx context.Context) {
	prefix := slik.PDFFolderPrefix(slik.CurrentFolder())
	listCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()

	keys, err := n.Storage.ListObjectsByPrefix(listCtx, prefix)
	if err != nil {
		slog.Error("slik notifier list R2 failed", "err", err)
		return
	}

	newFiles := make([]string, 0, len(keys))
	for _, k := range keys {
		if !strings.HasSuffix(k, ".pdf") {
			continue
		}
		// File KTP_*.pdf bukan laporan — skip.
		base := k
		if i := strings.LastIndex(k, "/"); i >= 0 {
			base = k[i+1:]
		}
		if strings.HasPrefix(base, "KTP_") {
			continue
		}
		exists, err := n.Notified.Exists(ctx, k)
		if err != nil {
			slog.Error("slik notifier check exists failed", "key", k, "err", err)
			continue
		}
		if !exists {
			newFiles = append(newFiles, k)
		}
	}

	if len(newFiles) == 0 {
		return
	}
	slog.Info("slik notifier found new files", "count", len(newFiles))

	byPrefix := groupByCodePrefix(newFiles)
	users, err := n.Users.FindAll(ctx)
	if err != nil {
		slog.Error("slik notifier load users failed", "err", err)
		return
	}

	for code, files := range byPrefix {
		for _, u := range users {
			if !strings.EqualFold(u.UserCode, code) {
				continue
			}
			msg := buildMessage(code, files)
			if _, err := n.Bot.SendText(u.ChatID, msg); err != nil {
				slog.Error("slik notifier send failed", "chat_id", u.ChatID, "err", err)
				continue
			}
			slog.Info("slik notification sent", "chat_id", u.ChatID, "code", code, "files", len(files))
		}
	}

	now := time.Now().UTC()
	for _, k := range newFiles {
		if err := n.Notified.Save(ctx, &entity.SlikNotifiedFile{FileKey: k, NotifiedAt: now}); err != nil {
			slog.Error("slik notifier mark failed", "key", k, "err", err)
		}
	}
}

func groupByCodePrefix(keys []string) map[string][]string {
	out := make(map[string][]string, 8)
	for _, k := range keys {
		base := k
		if i := strings.LastIndex(k, "/"); i >= 0 {
			base = k[i+1:]
		}
		code := base
		if i := strings.Index(base, "_"); i > 0 {
			code = base[:i]
		}
		out[code] = append(out[code], base)
	}
	return out
}

func buildMessage(code string, files []string) string {
	var b strings.Builder
	b.WriteString("📄 *SLIK Update*\n")
	fmt.Fprintf(&b, "Kode: *%s*\n", code)
	fmt.Fprintf(&b, "Total file: *%d*\n\n", len(files))
	b.WriteString("*Daftar File:*\n")
	for _, f := range files {
		fmt.Fprintf(&b, "• `%s`\n", f)
	}
	return b.String()
}
