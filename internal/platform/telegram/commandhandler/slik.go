package commandhandler

import (
	"context"
	"regexp"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram/keyboard"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
)

var ktpRe = regexp.MustCompile(`^\d{16}$`)

// Slik /slik — entry point pencarian SLIK. Pilih bulan dulu, eksekusi
// dilakukan callback handler "slikMonth" setelah user pilih bulan.
//
// Catatan: PDF generator (wkhtmltopdf) belum diport. Saat user memilih bulan,
// callback masih balas stub kalau handler-nya belum ada.
type Slik struct {
	Sessions *slik.SessionCache
}

func (h *Slik) Command() string     { return "/slik" }
func (h *Slik) Description() string { return "Cari data SLIK berdasarkan NIK (16 digit) atau nama" }

func (h *Slik) Handle(_ context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	query := strings.TrimSpace(strings.TrimPrefix(msg.Text, "/slik"))
	if query == "" {
		_, _ = b.SendText(chatID, "⚠️ No KTP harus diisi\n\nGunakan: `/slik <16 digit KTP>` atau `/slik <nama>`")
		return
	}

	t := slik.TypeName
	if ktpRe.MatchString(query) {
		t = slik.TypeKTP
	}
	h.Sessions.PutPending(chatID, slik.PendingQuery{Query: query, Type: t})
	_, _ = b.SendTextWithKeyboard(chatID, "📅 Pilih bulan dan tahun:", keyboard.SlikMonthPicker(12))
}

// DocSlik /doc — ambil dokumen SLIK by nama file (langkah 1: pilih bulan).
type DocSlik struct {
	Sessions *slik.SessionCache
}

func (h *DocSlik) Command() string     { return "/doc" }
func (h *DocSlik) Description() string { return "Ambil dokumen SLIK by nama file" }

func (h *DocSlik) Handle(_ context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	name := strings.TrimSpace(strings.TrimPrefix(msg.Text, "/doc"))
	if name == "" {
		_, _ = b.SendText(chatID, "⚠️ Nama file harus diisi\n\nGunakan: `/doc <nama_file>`")
		return
	}
	if strings.ContainsAny(name, `/\`) {
		_, _ = b.SendText(chatID, "⚠️ Cukup nama file saja, tanpa path/folder (mis. `/doc DNI_Andi.pdf`)")
		return
	}
	h.Sessions.PutPending(chatID, slik.PendingQuery{Query: name, Type: slik.TypeDoc})
	_, _ = b.SendTextWithKeyboard(chatID, "📅 Pilih bulan dan tahun:", keyboard.SlikMonthPicker(12))
}
