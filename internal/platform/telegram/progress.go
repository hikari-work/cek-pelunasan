package telegram

import (
	"fmt"
	"strings"
	"sync"
)

// ProgressReporter mengelola pesan progress upload — kirim baseline, edit
// saat ada update, lalu finalize ke pesan sukses/gagal. Aman dipakai
// dari goroutine berbeda dari yang membuatnya.
type ProgressReporter struct {
	bot       *Bot
	chatID    int64
	label     string
	total     int64
	mu        sync.Mutex
	messageID int
	done      bool
}

// NewProgressReporter mengirim pesan baseline 0% dan mengembalikan reporter
// yang siap dipakai. Kalau pesan baseline gagal, reporter tetap dikembalikan
// (call selanjutnya akan no-op).
func NewProgressReporter(b *Bot, chatID int64, label string, total int64) *ProgressReporter {
	r := &ProgressReporter{bot: b, chatID: chatID, label: label, total: total}
	id, err := b.SendText(chatID, r.format(0))
	if err == nil {
		r.messageID = id
	}
	return r
}

// Update edit pesan progress dengan jumlah baris yang sudah diproses.
func (r *ProgressReporter) Update(processed int64) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.done || r.messageID == 0 {
		return
	}
	_ = r.bot.EditText(r.chatID, r.messageID, r.format(processed))
}

// Finish edit pesan progress jadi pesan akhir (sukses/gagal). Setelah dipanggil,
// Update berikutnya tidak melakukan apa-apa.
func (r *ProgressReporter) Finish(text string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.done = true
	if r.messageID == 0 {
		_, _ = r.bot.SendText(r.chatID, text)
		return
	}
	_ = r.bot.EditText(r.chatID, r.messageID, text)
}

func (r *ProgressReporter) format(processed int64) string {
	const barWidth = 12
	percent := 0
	if r.total > 0 {
		percent = int(processed * 100 / r.total)
		if percent > 100 {
			percent = 100
		}
	}
	filled := percent * barWidth / 100
	empty := barWidth - filled
	bar := "[" + strings.Repeat("■", filled) + strings.Repeat("□", empty) + "]"
	return fmt.Sprintf("⬆️ Mengimpor %s\n%s %d%%\nProcessed: %d\nSize: %d",
		r.label, bar, percent, processed, r.total)
}
