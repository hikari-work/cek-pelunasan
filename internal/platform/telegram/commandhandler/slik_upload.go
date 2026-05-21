package commandhandler

import (
	"context"
	"log/slog"
	"path"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/r2"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
)

// SlikDocumentUpload terima dokumen yang dikirim Admin lalu unggah ke R2.
// Subfolder ditentukan ekstensi: pdf/txt/ideb. Folder = bulan saat ini (WIB).
type SlikDocumentUpload struct {
	Authed  *auth.AuthorizedChats
	Storage *r2.Client
}

func (h *SlikDocumentUpload) HandleDocument(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	if msg == nil || msg.Document == nil {
		return
	}
	chatID := msg.Chat.ID
	if !h.Authed.IsAuthorized(chatID) {
		return
	}
	role, err := h.Authed.Roles(ctx, chatID)
	if err != nil {
		slog.Error("check role for SLIK upload failed", "chat_id", chatID, "err", err)
		return
	}
	if role != entity.RoleAdmin {
		return
	}
	if h.Storage == nil {
		_, _ = b.SendText(chatID, "❌ Storage R2 belum dikonfigurasi")
		return
	}

	doc := msg.Document
	fileName := strings.TrimSpace(doc.FileName)
	if fileName == "" {
		_, _ = b.SendText(chatID, "⚠️ File tidak punya nama, abaikan")
		return
	}
	if strings.Contains(strings.ToLower(fileName), "empty") {
		_, _ = b.SendText(chatID, "⚠️ Nama file mengandung `Empty`, file ditolak")
		return
	}
	ext := strings.ToLower(strings.TrimPrefix(path.Ext(fileName), "."))
	sub, contentType := slik.SubfolderForExt(ext)
	if sub == "" {
		_, _ = b.SendText(chatID,
			"⚠️ Extension `"+ext+"` tidak didukung. Gunakan: pdf, txt, ideb")
		return
	}

	bytes, err := b.DownloadFile(doc.FileID)
	if err != nil {
		slog.Error("download telegram file failed", "file_id", doc.FileID, "err", err)
		_, _ = b.SendText(chatID, "❌ Gagal mengunduh file: "+err.Error())
		return
	}

	folder := slik.CurrentFolder()
	key := folder + "/" + sub + "/" + fileName
	if err := h.Storage.PutObject(ctx, key, bytes, contentType); err != nil {
		slog.Error("R2 upload failed", "key", key, "err", err)
		_, _ = b.SendText(chatID, "❌ Gagal upload ke R2: "+err.Error())
		return
	}
	slog.Info("SLIK upload success", "key", key, "size", len(bytes), "chat_id", chatID)
	_, _ = b.SendText(chatID, "✅ Upload berhasil: `"+key+"`")
	go func() { _ = b.DeleteMessage(chatID, msg.MessageID) }()
}
