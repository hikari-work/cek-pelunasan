// Package callbackhandler — handler SLIK month/name/sender.
package callbackhandler

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
	"github.com/hikari-work/cek-pelunasan/internal/platform/r2"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram/keyboard"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

// SlikMonth handler callback "slikMonth_<yyyymm>". Tergantung pending type:
//   - doc:  fetch dari pdf/txt/ideb subfolder, kirim file langsung
//   - ktp:  tampilkan keyboard pilih jenis laporan (aktif / semua)
//   - name: list folder pdf/, filter by query, simpan halaman ke session,
//     kirim halaman 1 + keyboard pagination.
type SlikMonth struct {
	Sessions *slik.SessionCache
	Storage  *r2.Client
	Users    *users.Service
}

func (h *SlikMonth) Prefix() string { return "slikMonth" }

func (h *SlikMonth) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	chatID := q.Message.Chat.ID
	parts := strings.SplitN(q.Data, "_", 2)
	if len(parts) < 2 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	yyyymm := parts[1]
	if len(yyyymm) != 6 {
		_ = b.AnswerCallback(q.ID, "Format bulan tidak valid")
		return
	}

	pending, ok := h.Sessions.TakePending(chatID)
	if !ok {
		_, _ = b.SendText(chatID, "⚠️ Sesi pencarian sudah habis, ulangi `/slik <query>`")
		return
	}

	// Delete async — flow lanjut tanpa nunggu round-trip ke Telegram.
	msgID := q.Message.MessageID
	go func() { _ = b.DeleteMessage(chatID, msgID) }()
	folder := slik.FolderForMonth(yyyymm)

	switch pending.Type {
	case slik.TypeDoc:
		h.handleDoc(ctx, b, chatID, folder, pending.Query)
	case slik.TypeKTP:
		_, _ = b.SendTextWithKeyboard(chatID, "📋 Pilih jenis laporan SLIK:",
			keyboard.SlikSenderConfirmation(pending.Query, yyyymm))
	case slik.TypeName:
		h.handleNameSearch(ctx, b, chatID, folder, pending.Query)
	default:
		_, _ = b.SendText(chatID, "⚠️ Tipe pencarian tidak dikenal")
	}
}

func (h *SlikMonth) handleDoc(ctx context.Context, b *telegram.Bot, chatID int64, folder, name string) {
	if h.Storage == nil {
		_, _ = b.SendText(chatID, "❌ Storage R2 belum dikonfigurasi")
		return
	}
	for _, key := range []string{
		slik.PDFKey(folder, name),
		slik.TXTKey(folder, name),
		slik.IDEBKey(folder, name),
	} {
		data, err := h.Storage.GetObject(ctx, key)
		if err != nil {
			slog.Warn("get object failed", "key", key, "err", err)
			continue
		}
		if len(data) == 0 {
			continue
		}
		if err := b.SendDocument(chatID, name, data); err != nil {
			slog.Error("send document failed", "key", key, "err", err)
			_, _ = b.SendText(chatID, "❌ Gagal mengirim dokumen")
		}
		return
	}
	_, _ = b.SendText(chatID, "❌ File tidak ditemukan")
}

func (h *SlikMonth) handleNameSearch(ctx context.Context, b *telegram.Bot, chatID int64, folder, query string) {
	if h.Storage == nil {
		_, _ = b.SendText(chatID, "❌ Storage R2 belum dikonfigurasi")
		return
	}

	user, err := h.Users.FindByChatID(ctx, chatID)
	if err != nil || user == nil {
		_, _ = b.SendText(chatID, "❌ User tidak dikenali, hubungi Admin")
		return
	}
	isAdmin := user.Roles == entity.RoleAdmin || user.Roles == entity.RolePIMP

	loadingMsg, _ := b.SendText(chatID, "⏳ Mencari data SLIK...")

	prefix := slik.PDFFolderPrefix(folder)
	listCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	keys, err := h.Storage.ListObjectsByPrefix(listCtx, prefix)
	if err != nil {
		slog.Error("list R2 failed", "prefix", prefix, "err", err)
		_, _ = b.SendText(chatID, "❌ Gagal mengambil data dari penyimpanan")
		return
	}

	queryLower := strings.ToLower(query)
	pages := make([]slik.PageData, 0, 16)
	for _, key := range keys {
		if strings.Contains(key, "KTP_") {
			continue
		}
		if !strings.Contains(strings.ToLower(key), queryLower) {
			continue
		}
		// Filter AO: kalau bukan admin, hanya file dengan prefix userCode_.
		if !isAdmin && user.UserCode != "" && !strings.Contains(key, "/"+user.UserCode+"_") {
			continue
		}
		page := h.buildPageData(ctx, key, folder)
		pages = append(pages, page)
	}

	// Hapus pesan loading.
	if loadingMsg > 0 {
		_ = b.DeleteMessage(chatID, loadingMsg)
	}

	if len(pages) == 0 {
		_, _ = b.SendText(chatID, "❌ Tidak ada data SLIK ditemukan untuk bulan tersebut")
		return
	}

	h.Sessions.PutSession(chatID, pages, query)
	text := slik.FormatPage(pages[0], 0, len(pages))
	kb := keyboard.SlikNamePagination(0, len(pages))
	_, _ = b.SendTextWithKeyboard(chatID, text, kb)
	slog.Info("slik name search", "chat_id", chatID, "folder", folder, "results", len(pages))
}

// buildPageData fetch PDF, ekstrak NIK, lalu coba load+parse TXT untuk DTO.
// Tidak fail kalau salah satu langkah error — tetap kembalikan PageData
// dengan field yang ada supaya pesan no-data masih punya konteks.
func (h *SlikMonth) buildPageData(ctx context.Context, contentKey, folder string) slik.PageData {
	page := slik.PageData{ContentKey: contentKey}
	pdfBytes, err := h.Storage.GetObject(ctx, contentKey)
	if err != nil || len(pdfBytes) == 0 {
		return page
	}
	id, err := slik.ExtractNIK(pdfBytes)
	if err != nil {
		slog.Debug("extract NIK failed", "key", contentKey, "err", err)
		return page
	}
	page.IDNumber = id
	if id == "" {
		return page
	}
	txtKey := slik.KTPTextKey(folder, id)
	txtBytes, err := h.Storage.GetObject(ctx, txtKey)
	if err != nil || len(txtBytes) == 0 {
		return page
	}
	dto, err := slik.ParseSlikJSON(txtBytes)
	if err != nil {
		slog.Debug("parse SLIK JSON failed", "key", txtKey, "err", err)
		return page
	}
	page.DTO = dto
	return page
}

// SlikName handler pagination "slikName_<page>" — edit pesan dengan halaman baru.
type SlikName struct {
	Sessions *slik.SessionCache
}

func (h *SlikName) Prefix() string { return "slikName" }

func (h *SlikName) Handle(_ context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	chatID := q.Message.Chat.ID
	parts := strings.SplitN(q.Data, "_", 2)
	if len(parts) < 2 {
		_ = b.AnswerCallback(q.ID, "Data callback tidak valid")
		return
	}
	page, err := strconv.Atoi(parts[1])
	if err != nil {
		_ = b.AnswerCallback(q.ID, "Halaman tidak valid")
		return
	}
	sess, ok := h.Sessions.GetSession(chatID)
	if !ok || len(sess.Pages) == 0 {
		_, _ = b.SendText(chatID, "⚠️ Sesi pencarian sudah habis, ulangi `/slik <nama>`")
		return
	}
	if page < 0 || page >= len(sess.Pages) {
		_ = b.AnswerCallback(q.ID, "Halaman di luar rentang")
		return
	}
	text := slik.FormatPage(sess.Pages[page], page, len(sess.Pages))
	kb := keyboard.SlikNamePagination(page, len(sess.Pages))
	_ = b.EditTextWithMarkup(chatID, q.Message.MessageID, text, kb)
}

// SlikSender handler "slikSender_<yyyymm>_<ktpId>_<active>_<endpoint>" — fetch KTP txt dari R2,
// generate PDF via PDFGenerator (PHP) atau HTMLGenerator (Go), kirim sebagai dokumen.
type SlikSender struct {
	Storage       *r2.Client
	Generator     *slik.PDFGenerator  // PHP endpoint
	HTMLGenerator *slik.HTMLGenerator // Go native
	Users         *users.Service
	MaxPDFSize    int64
}

func (h *SlikSender) Prefix() string { return "slikSender" }

func (h *SlikSender) Handle(ctx context.Context, b *telegram.Bot, q *tgbotapi.CallbackQuery) {
	chatID := q.Message.Chat.ID
	parts := strings.SplitN(q.Data, "_", 5)
	if len(parts) < 4 {
		_, _ = b.SendText(chatID, "⚠️ Format callback tidak valid")
		return
	}
	yyyymm := parts[1]
	customerID := parts[2]
	active := parts[3] == "1"

	// Endpoint type: "php" or "go" (default to php for backward compatibility)
	endpoint := "php"
	if len(parts) >= 5 {
		endpoint = parts[4]
	}

	if len(yyyymm) != 6 {
		_, _ = b.SendText(chatID, "⚠️ Format bulan tidak valid")
		return
	}

	// Edit pesan keyboard jadi loading sekaligus drop tombol — 1 round-trip,
	// menggantikan delete + send terpisah.
	loadingID := q.Message.MessageID
	if err := b.EditTextWithMarkup(chatID, loadingID, "⏳ Mengambil Data KTP...",
		tgbotapi.InlineKeyboardMarkup{InlineKeyboard: [][]tgbotapi.InlineKeyboardButton{}}); err != nil {
		// Fallback: pesan asli sudah hilang / tidak bisa di-edit. Kirim baru.
		newID, sendErr := b.SendText(chatID, "⏳ Mengambil Data KTP...")
		if sendErr != nil {
			slog.Error("send loading failed", "err", sendErr)
			return
		}
		loadingID = newID
	}

	folder := slik.FolderForMonth(yyyymm)
	key := slik.KTPTextKey(folder, customerID)

	if h.Storage == nil {
		_ = b.EditText(chatID, loadingID, "❌ Storage R2 belum dikonfigurasi")
		return
	}

	getCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	data, err := h.Storage.GetObject(getCtx, key)
	if err != nil {
		slog.Error("get KTP failed", "key", key, "err", err)
		_ = b.EditText(chatID, loadingID, "⚠️ Terjadi kesalahan saat memproses data. Silakan coba lagi.")
		return
	}
	if len(data) == 0 {
		_ = b.EditText(chatID, loadingID, fmt.Sprintf("❌ Data KTP `%s` tidak ditemukan", customerID))
		return
	}
	if h.MaxPDFSize > 0 && int64(len(data)) > h.MaxPDFSize {
		_ = b.EditText(chatID, loadingID, "❌ File terlalu besar untuk diproses")
		return
	}

	var pdfBytes []byte

	if endpoint == "go" {
		// Use Go native HTML generator
		if h.HTMLGenerator == nil {
			_ = b.EditText(chatID, loadingID, "❌ Go HTML generator belum dikonfigurasi")
			return
		}

		_ = b.EditText(chatID, loadingID, fmt.Sprintf("✅ Data KTP `%s` ditemukan. Menggenerate PDF (Go)...", customerID))

		genCtx, cancelGen := context.WithTimeout(ctx, 90*time.Second)
		defer cancelGen()

		requesterName := q.From.FirstName
		if q.From.LastName != "" {
			requesterName += " " + q.From.LastName
		}

		pdfBytes, err = h.generatePDFWithGo(genCtx, chatID, data, active, requesterName)
		if err != nil {
			slog.Error("generate pdf with go failed", "customer_id", customerID, "err", err)
			_ = b.EditText(chatID, loadingID, fmt.Sprintf("❌ Gagal generate PDF (Go): %s", err.Error()))
			return
		}
	} else {
		// Use PHP endpoint (legacy)
		if h.Generator == nil {
			_ = b.EditText(chatID, loadingID, "❌ PDF generator belum dikonfigurasi")
			return
		}

		_ = b.EditText(chatID, loadingID, fmt.Sprintf("✅ Data KTP `%s` ditemukan. Menggenerate PDF (PHP)...", customerID))

		genCtx, cancelGen := context.WithTimeout(ctx, 90*time.Second)
		defer cancelGen()
		pdfBytes, err = h.Generator.Generate(genCtx, data, active)
		if err != nil {
			slog.Error("generate pdf with php failed", "customer_id", customerID, "err", err)
			_ = b.EditText(chatID, loadingID, fmt.Sprintf("❌ Gagal generate PDF (PHP): %s", err.Error()))
			return
		}
	}

	if len(pdfBytes) == 0 {
		_ = b.EditText(chatID, loadingID, fmt.Sprintf("❌ Data KTP `%s` tidak ditemukan", customerID))
		return
	}

	// Delete loading async — kirim PDF langsung supaya user nggak nunggu
	// round-trip delete dulu.
	go func() { _ = b.DeleteMessage(chatID, loadingID) }()
	if err := b.SendDocument(chatID, customerID+".pdf", pdfBytes); err != nil {
		slog.Error("send pdf failed", "customer_id", customerID, "err", err)
		_, _ = b.SendText(chatID, "❌ Gagal mengirim PDF")
		return
	}
	slog.Info("slik PDF sent", "customer_id", customerID, "active", active, "endpoint", endpoint, "size", len(pdfBytes))
}

// generatePDFWithGo generates PDF using Go native HTMLGenerator + wkhtmltopdf.
// This is more efficient than calling PHP endpoint via HTTP.
func (h *SlikSender) generatePDFWithGo(ctx context.Context, chatID int64, slikData []byte, fasilitasAktif bool, requesterName string) ([]byte, error) {
	// Parse SLIK JSON
	dto, err := slik.ParseSlikJSON(slikData)
	if err != nil {
		return nil, fmt.Errorf("parse slik json: %w", err)
	}

	// Populate requester info if possible
	if dto.Header.TujuanPenggunaan == "" {
		dto.Header.TujuanPenggunaan = "Analisa Kredit"
	}
	if dto.Header.PetugasPermintaan == "" {
		if requesterName != "" {
			dto.Header.PetugasPermintaan = requesterName
		} else if h.Users != nil {
			user, err := h.Users.FindByChatID(ctx, chatID)
			if err == nil && user != nil && user.UserCode != "" {
				dto.Header.PetugasPermintaan = user.UserCode
			}
		}
	}

	// Generate HTML
	html := h.HTMLGenerator.GenerateHTML(dto, fasilitasAktif)
	if html == "" {
		return nil, fmt.Errorf("html generation returned empty result")
	}

	// Convert HTML to PDF using wkhtmltopdf
	pdfBytes, err := slik.RenderHTMLToPDF(ctx, html, 90*time.Second)
	if err != nil {
		return nil, fmt.Errorf("render pdf: %w", err)
	}

	return pdfBytes, nil
}
