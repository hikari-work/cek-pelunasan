package commandhandler

import (
	"context"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram/keyboard"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/service/minbunga"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// MinBunga /minbunga — kalkulator tagihan minimal bunga.
//
//   - AO: langsung tampil kalender pilih tanggal target.
//   - PIMP/ADMIN: pilih cabang dulu, baru kalender (lewat callback).
//
// Eksekusi pencarian (setelah konfirmasi tanggal) ditangani oleh callback
// handler — di sini hanya entry point.
type MinBunga struct {
	Users    *users.Service
	Bills    *bill.Service
	Sessions *minbunga.SessionService
	Log      *log.Service
}

func (h *MinBunga) Command() string { return "/minbunga" }
func (h *MinBunga) Description() string {
	return "Tagihan minimal bunga berdasarkan tanggal target"
}

func (h *MinBunga) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID

	if !h.dataFresh(ctx, b, chatID) {
		return
	}

	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		_, _ = b.SendText(chatID, "❌ *User tidak ditemukan*")
		return
	}

	switch user.Roles {
	case entity.RoleAO:
		h.startAOFlow(ctx, b, chatID, user.UserCode)
	case entity.RolePIMP, entity.RoleAdmin:
		h.startBranchFlow(ctx, b, chatID, user.UserCode)
	default:
		_, _ = b.SendText(chatID, "❌ *Role tidak diizinkan*")
	}
}

func (h *MinBunga) dataFresh(ctx context.Context, b *telegram.Bot, chatID int64) bool {
	warn := h.Log.TelegramWarning(ctx, "TAGIHAN")
	if warn == "" {
		return true
	}
	_, _ = b.SendText(chatID,
		"❌ *Data TAGIHAN belum diperbarui hari ini.*"+warn+
			"\n_Mohon import ulang data sebelum menggunakan fitur ini._")
	return false
}

func (h *MinBunga) startAOFlow(ctx context.Context, b *telegram.Bot, chatID int64, userCode string) {
	if prev, _ := h.Sessions.Get(ctx, chatID); prev != nil && prev.MessageID > 0 {
		_ = b.DeleteMessage(chatID, int(prev.MessageID))
	}
	if _, err := h.Sessions.GetOrCreate(ctx, chatID, userCode, "AO"); err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal memulai sesi: "+err.Error())
		return
	}
	kb := keyboard.MinBungaCalendar(userCode, nil, false)
	id, err := b.SendTextWithKeyboard(chatID,
		"📅 *Pilih Tanggal Penagihan*\n\n"+
			"_Pilih satu atau beberapa tanggal target penagihan._\n"+
			"_Bot akan menampilkan nasabah yang DayLate-nya tidak melebihi 90 hari pada tanggal tersebut._",
		kb)
	if err == nil && id > 0 {
		_, _ = h.Sessions.SetMessageID(ctx, chatID, int64(id))
	}
}

func (h *MinBunga) startBranchFlow(ctx context.Context, b *telegram.Bot, chatID int64, userCode string) {
	if prev, _ := h.Sessions.Get(ctx, chatID); prev != nil && prev.MessageID > 0 {
		_ = b.DeleteMessage(chatID, int(prev.MessageID))
	}
	branches, err := h.Bills.ListAllBranches(ctx)
	if err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal mengambil daftar cabang: "+err.Error())
		return
	}
	if len(branches) == 0 {
		_, _ = b.SendText(chatID, "❌ *Belum ada cabang terdaftar*")
		return
	}
	kb := keyboard.MinBungaBranchPicker(branches)
	id, err := b.SendTextWithKeyboard(chatID,
		"🏦 *Pilih Cabang*\n\n_Pilih cabang yang akan dicek tagihan minimal bunganya._", kb)
	if err != nil {
		return
	}
	if _, err := h.Sessions.GetOrCreate(ctx, chatID, userCode, "BRANCH"); err == nil && id > 0 {
		_, _ = h.Sessions.SetMessageID(ctx, chatID, int64(id))
	}
}
