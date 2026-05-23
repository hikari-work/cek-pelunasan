package callbackhandler

import (
	"context"
	"fmt"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram/keyboard"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/minbunga"
)

// MinBungaBranch /minbunga branch picker (PIMP/ADMIN flow).
// Callback "minbunga_<branch>" — replace pesan "Pilih Cabang" jadi kalender.
type MinBungaBranch struct {
	Sessions *minbunga.SessionService
}

func (h *MinBungaBranch) Prefix() string { return "minbunga" }

func (h *MinBungaBranch) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 2)
	if len(parts) < 2 {
		_ = b.AnswerCallback(q.ID, "Data tidak valid")
		return
	}
	branch := parts[1]
	chatID := q.Message.Chat.ID
	messageID := q.Message.MessageID

	sess, _ := h.Sessions.Get(ctx, chatID)
	if sess != nil && sess.MessageID > 0 && sess.MessageID != int64(messageID) {
		_ = b.AnswerCallback(q.ID, "Sesi sudah berpindah")
		return
	}
	if _, err := h.Sessions.GetOrCreate(ctx, chatID, branch, "BRANCH"); err != nil {
		_ = b.AnswerCallback(q.ID, "Gagal mulai sesi")
		return
	}
	if _, err := h.Sessions.SetMessageID(ctx, chatID, int64(messageID)); err != nil {
		_ = b.AnswerCallback(q.ID, "Gagal mulai sesi")
		return
	}
	caption := minBungaCaption(branch, "BRANCH", 0)
	_ = b.EditTextWithMarkup(chatID, messageID, caption, keyboard.MinBungaCalendar(branch, nil, false))
	_ = b.AnswerCallback(q.ID, "")
}

// MinBungaCalendarToggle callback "minbungaCal_<id>_YYYY-MM-DD".
type MinBungaCalendarToggle struct {
	Sessions *minbunga.SessionService
}

func (h *MinBungaCalendarToggle) Prefix() string { return "minbungaCal" }

func (h *MinBungaCalendarToggle) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 3)
	if len(parts) < 3 {
		_ = b.AnswerCallback(q.ID, "Data tidak valid")
		return
	}
	identifier, date := parts[1], parts[2]
	chatID := q.Message.Chat.ID
	messageID := q.Message.MessageID

	sess, _ := h.Sessions.Get(ctx, chatID)
	if sess == nil || (sess.MessageID > 0 && sess.MessageID != int64(messageID)) {
		_ = b.AnswerCallback(q.ID, "Sesi habis, jalankan /minbunga lagi")
		return
	}
	updated, err := h.Sessions.ToggleDate(ctx, chatID, date)
	if err != nil || updated == nil {
		_ = b.AnswerCallback(q.ID, "Gagal memperbarui pilihan")
		return
	}
	hasSelection := len(updated.SelectedDates) > 0
	caption := minBungaCaption(identifier, updated.Role, len(updated.SelectedDates))
	_ = b.EditTextWithMarkup(chatID, messageID, caption,
		keyboard.MinBungaCalendar(identifier, updated.SelectedDates, hasSelection))
	_ = b.AnswerCallback(q.ID, "")
}

// MinBungaClear callback "minbungaClear_<id>" — kosongkan tanggal terpilih.
type MinBungaClear struct {
	Sessions *minbunga.SessionService
}

func (h *MinBungaClear) Prefix() string { return "minbungaClear" }

func (h *MinBungaClear) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(q.Data, "_", 2)
	if len(parts) < 2 {
		_ = b.AnswerCallback(q.ID, "Data tidak valid")
		return
	}
	identifier := parts[1]
	chatID := q.Message.Chat.ID
	messageID := q.Message.MessageID

	sess, _ := h.Sessions.Get(ctx, chatID)
	if sess == nil || (sess.MessageID > 0 && sess.MessageID != int64(messageID)) {
		_ = b.AnswerCallback(q.ID, "Sesi habis")
		return
	}
	if _, err := h.Sessions.ClearDates(ctx, chatID); err != nil {
		_ = b.AnswerCallback(q.ID, "Gagal mereset")
		return
	}
	caption := minBungaCaption(identifier, sess.Role, 0)
	_ = b.EditTextWithMarkup(chatID, messageID, caption, keyboard.MinBungaCalendar(identifier, nil, false))
	_ = b.AnswerCallback(q.ID, "")
}

// MinBungaConfirm callback "minbungaConfirm_<id>" — eksekusi pencarian dan kirim
// hasil per chunk pesan, lalu hapus sesi.
type MinBungaConfirm struct {
	Sessions *minbunga.SessionService
	Bills    *bill.Service
}

func (h *MinBungaConfirm) Prefix() string { return "minbungaConfirm" }

func (h *MinBungaConfirm) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	chatID := q.Message.Chat.ID
	messageID := q.Message.MessageID
	sess, err := h.Sessions.Get(ctx, chatID)
	if err != nil || sess == nil {
		_ = b.AnswerCallback(q.ID, "Sesi tidak ditemukan")
		_, _ = b.SendText(chatID, "❌ *Sesi tidak ditemukan. Mulai ulang dengan* `/minbunga`")
		return
	}
	if sess.MessageID > 0 && sess.MessageID != int64(messageID) {
		_ = b.AnswerCallback(q.ID, "Sesi sudah berpindah")
		return
	}
	if len(sess.SelectedDates) == 0 {
		_ = b.AnswerCallback(q.ID, "Belum ada tanggal yang dipilih")
		return
	}

	wib := time.FixedZone("WIB", 7*3600)
	targets := make([]time.Time, 0, len(sess.SelectedDates))
	for _, d := range sess.SelectedDates {
		t, err := time.ParseInLocation("2006-01-02", d, wib)
		if err == nil {
			targets = append(targets, t)
		}
	}
	if len(targets) == 0 {
		_ = b.AnswerCallback(q.ID, "Tanggal tidak valid")
		return
	}

	_ = b.EditText(chatID, messageID, "⏳ *Sedang memproses data...*\n_Mohon tunggu sebentar._")
	_ = b.AnswerCallback(q.ID, "")

	minDayLate := minbunga.MinDayLateThreshold(targets)
	var bills []entity.Bills
	if sess.Role == "AO" {
		bills, err = h.Bills.FindMinimalBungaByAO(ctx, sess.Identifier, minDayLate)
	} else {
		bills, err = h.Bills.FindMinimalBungaByBranch(ctx, sess.Identifier, minDayLate)
	}
	if err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal mengambil data: "+err.Error())
		return
	}

	grouped := minbunga.Calculate(bills, targets)
	for _, msg := range minbunga.FormatMessages(grouped, sess.Identifier) {
		_, _ = b.SendText(chatID, msg)
	}
	_ = h.Sessions.Delete(ctx, chatID)
}

func minBungaCaption(identifier, role string, selectedCount int) string {
	header := "📅 *Pilih Tanggal Penagihan*"
	if role != "AO" {
		header += " — Cabang: " + identifier
	}
	caption := header +
		"\n\n_Pilih satu atau beberapa tanggal target penagihan._" +
		"\n_Bot akan menampilkan nasabah yang DayLate-nya tidak melebihi 90 hari pada tanggal tersebut._"
	if selectedCount > 0 {
		caption += fmt.Sprintf("\n\n✅ *Tanggal terpilih: %d*", selectedCount)
	}
	return caption
}
