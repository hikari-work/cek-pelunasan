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
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// MinBungaBranch /minbunga branch picker (PIMP/ADMIN flow).
// Callback: "minbunga_<branch>" — replace pesan "Pilih Cabang" jadi kalender.
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

// MinBungaCalendarToggle: callback "minbungaCal_<id>_YYYY-MM-DD".
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

// MinBungaClear: callback "minbungaClear_<id>" — kosongkan tanggal terpilih.
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

// MinBungaConfirm: callback "minbungaConfirm_<id>" — eksekusi pencarian dan kirim
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
	for _, msg := range formatMinBungaMessages(grouped, sess.Identifier) {
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

const maxMinBungaChars = 3800

// formatMinBungaMessages chunk hasil per kelompok tanggal supaya tidak melebihi
// batas char Telegram. Padanan MinBungaMessageFormatter.format dari legacy.
func formatMinBungaMessages(groups []minbunga.BillsForDate, identifier string) []string {
	wib := time.FixedZone("WIB", 7*3600)
	today := time.Now().In(wib)
	var messages []string

	for _, g := range groups {
		header := buildMinBungaHeader(g.TargetDate, g.DaysDiff, identifier, len(g.Bills))
		current := header
		for _, db := range g.Bills {
			entryStr := buildMinBungaEntry(db, today)
			if len(current)+len(entryStr) > maxMinBungaChars {
				messages = append(messages, current)
				current = "_Lanjutan " + formatTanggalID(g.TargetDate) + "_\n\n"
			}
			current += entryStr
		}
		if current != "" {
			messages = append(messages, current)
		}
	}

	if len(messages) == 0 {
		messages = append(messages,
			"*Tidak ada tagihan yang memenuhi kriteria.*\n_Semua nasabah masih aman dalam batas DayLate 90 hari._")
	}
	return messages
}

func buildMinBungaHeader(date time.Time, daysDiff int, identifier string, count int) string {
	return "*Tagihan: " + formatTanggalID(date) + "* (+" + fmt.Sprintf("%d", daysDiff) + " hari)\n" +
		"Minimal bayar Maksimal di: " + formatTanggalDayID(date) + "\n" +
		"ID: " + identifier + " | Jumlah: " + fmt.Sprintf("%d", count) + " tagihan\n" +
		"─────────────────────\n\n"
}

func buildMinBungaEntry(db minbunga.DatedBill, today time.Time) string {
	bill := db.Bill
	threshold := 90 - db.DayLate
	if threshold < 0 {
		threshold = 0
	}
	maksBayar := today.AddDate(0, 0, threshold)
	jikaNotPay := bill.LastPrincipal + bill.Principal + bill.MinInterest

	return "*" + bill.Name + "*\n" +
		"Alamat: " + bill.Address + "\n" +
		"AO: " + bill.AccountOfficer + "\n\n" +
		"Plafond: " + utils.FormatRupiah(bill.Plafond) + "\n" +
		"Baki Debet: " + utils.FormatRupiah(bill.DebitTray) + "\n" +
		"Tgg. Pokok: " + utils.FormatRupiah(bill.LastPrincipal) + "\n" +
		"Tgg. Bunga: " + utils.FormatRupiah(bill.LastInterest) + "\n" +
		"Min. Pokok: " + utils.FormatRupiah(bill.MinPrincipal) + "\n" +
		"Min. Bunga: " + utils.FormatRupiah(bill.MinInterest) + "\n\n" +
		"Maks. Bayar: " + formatTanggalDayID(maksBayar) + "\n" +
		"Jika Tdk Bayar: " + utils.FormatRupiah(jikaNotPay) + "\n" +
		"─────────────────────\n\n"
}

func formatTanggalID(t time.Time) string {
	bulan := []string{"Januari", "Februari", "Maret", "April", "Mei", "Juni",
		"Juli", "Agustus", "September", "Oktober", "November", "Desember"}
	return fmt.Sprintf("%d %s %d", t.Day(), bulan[int(t.Month())-1], t.Year())
}

func formatTanggalDayID(t time.Time) string {
	hari := []string{"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"}
	return hari[int(t.Weekday())] + ", " + formatTanggalID(t)
}
