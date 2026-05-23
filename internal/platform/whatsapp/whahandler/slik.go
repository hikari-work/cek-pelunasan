package whahandler

import (
	"context"
	"errors"
	"log/slog"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/platform/r2"
	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
)

// Slik menangani perintah "{prefix}slik {nama}" dari WhatsApp.
//
// Flow:
//
//  1. Validasi input.
//  2. List PDF di R2 folder bulan berjalan ({folder}/pdf/).
//  3. Match nama pertama yang mengandung query (case-insensitive),
//     abaikan file dengan prefix "KTP_" (file metadata).
//  4. Fetch PDF asli, ekstrak NIK, fetch txt KTP, generate 2 PDF
//     (fasilitas aktif & semua) lewat slik.PDFGenerator.
//  5. Kirim 3 dokumen: PDF asli, PDF fasilitas aktif, PDF semua.
//
// Fallback kalau langkah 4 gagal di bagian generate (NIK tidak ketemu,
// txt tidak ada, wkhtmltopdf error, dst), kirim PDF asli saja dengan
// caption yang menjelaskan kondisi.
type Slik struct {
	Storage   *r2.Client
	Generator *slik.PDFGenerator
	Sender    *whatsapp.Sender
	Router    *whatsapp.Router
	Prefix    string // default "." kalau kosong

	// FolderProvider opsional untuk testing — return folder bulan ("2026_05").
	// Default pakai slik.CurrentFolder() (WIB now).
	FolderProvider func() string

	// MaxPDFSize batas ukuran PDF asli yang diproses (bytes). 0 = tidak ada batas.
	MaxPDFSize int64
}

func (h *Slik) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil {
		return false
	}
	return strings.HasPrefix(m.Body, prefixed(h.Prefix, "slik")+" ")
}

func (h *Slik) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil {
		return
	}

	chat := m.ChatJID()
	cmd := prefixed(h.Prefix, "slik")
	nama := strings.TrimSpace(strings.TrimPrefix(m.Body, cmd+" "))
	if nama == "" {
		_, _ = h.Sender.SendText(ctx, chat, "Format: "+cmd+" {nama nasabah}", &m.Info)
		return
	}

	if h.Storage == nil {
		_, _ = h.Sender.SendText(ctx, chat, "❌ Storage R2 belum dikonfigurasi", &m.Info)
		return
	}

	folder := h.currentFolder()
	prefix := slik.PDFFolderPrefix(folder)

	listCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	keys, err := h.Storage.ListObjectsByPrefix(listCtx, prefix)
	if err != nil {
		slog.Error("slik wa: list R2 gagal", "prefix", prefix, "err", err)
		_, _ = h.Sender.SendText(ctx, chat, "❌ Gagal mengambil data dari penyimpanan", &m.Info)
		return
	}

	pdfKey := matchSlikFile(nama, keys)
	if pdfKey == "" {
		_, _ = h.Sender.SendText(ctx, chat, "❌ Data tidak ditemukan untuk: *"+nama+"*", &m.Info)
		return
	}

	slog.Info("slik wa: match", "key", pdfKey, "query", nama)
	_, _ = h.Sender.SendText(ctx, chat, "⏳ Data ditemukan, sedang memproses 3 file PDF...", &m.Info)

	getCtx, cancelGet := context.WithTimeout(ctx, 30*time.Second)
	defer cancelGet()
	pdfBytes, err := h.Storage.GetObject(getCtx, pdfKey)
	if err != nil {
		slog.Error("slik wa: ambil PDF gagal", "key", pdfKey, "err", err)
		_, _ = h.Sender.SendText(ctx, chat, "❌ Gagal mengambil file dari server.", &m.Info)
		return
	}
	if len(pdfBytes) == 0 {
		_, _ = h.Sender.SendText(ctx, chat, "❌ File PDF kosong di server.", &m.Info)
		return
	}
	if h.MaxPDFSize > 0 && int64(len(pdfBytes)) > h.MaxPDFSize {
		_, _ = h.Sender.SendText(ctx, chat, "❌ File terlalu besar untuk diproses", &m.Info)
		return
	}

	display := extractSlikDisplayName(pdfKey)
	originalName := "SLIK_Asli_" + display + ".pdf"

	aktifPDF, semuaPDF, genErr := h.buildGeneratedPDFs(ctx, pdfBytes, pdfKey)
	if genErr != nil {
		slog.Warn("slik wa: generate PDF gagal, fallback kirim PDF asli", "key", pdfKey, "err", genErr)
		if err := h.Sender.SendDocument(ctx, chat, pdfBytes, originalName,
			"📄 SLIK Asli (generate PDF tidak tersedia)"); err != nil {
			slog.Error("slik wa: kirim PDF asli (fallback) gagal", "err", err)
			_, _ = h.Sender.SendText(ctx, chat, "❌ Gagal mengirim file.", &m.Info)
		}
		return
	}

	if err := h.Sender.SendDocument(ctx, chat, pdfBytes, originalName, "📄 1/3 — SLIK Asli"); err != nil {
		slog.Error("slik wa: kirim PDF asli gagal", "err", err)
		_, _ = h.Sender.SendText(ctx, chat, "❌ Gagal mengirim file.", &m.Info)
		return
	}
	if err := h.Sender.SendDocument(ctx, chat, aktifPDF, "SLIK_Aktif_"+display+".pdf",
		"✅ 2/3 — Fasilitas Aktif"); err != nil {
		slog.Error("slik wa: kirim PDF aktif gagal", "err", err)
		return
	}
	if err := h.Sender.SendDocument(ctx, chat, semuaPDF, "SLIK_Semua_"+display+".pdf",
		"📋 3/3 — Semua Fasilitas"); err != nil {
		slog.Error("slik wa: kirim PDF semua gagal", "err", err)
		return
	}
	slog.Info("slik wa: flow selesai", "key", pdfKey, "chat", chat.String())
}

// buildGeneratedPDFs derive folder dari PDF key, extract NIK, fetch txt,
// lalu generate 2 PDF. Kembalikan error kalau salah satu langkah gagal —
// caller akan fallback kirim PDF asli saja.
func (h *Slik) buildGeneratedPDFs(ctx context.Context, pdfBytes []byte, pdfKey string) (aktif, semua []byte, err error) {
	if h.Generator == nil {
		return nil, nil, errors.New("PDF generator tidak terkonfigurasi")
	}
	nik, err := slik.ExtractNIK(pdfBytes)
	if err != nil {
		return nil, nil, err
	}
	if nik == "" {
		return nil, nil, errors.New("nomor KTP tidak ditemukan di PDF")
	}

	folder := folderFromPDFKey(pdfKey)
	if folder == "" {
		folder = h.currentFolder()
	}

	txtCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	txtBytes, err := h.Storage.GetObject(txtCtx, slik.KTPTextKey(folder, nik))
	if err != nil {
		return nil, nil, err
	}
	if len(txtBytes) == 0 {
		return nil, nil, errors.New("file metadata KTP tidak ditemukan di server")
	}

	genCtx, cancelGen := context.WithTimeout(ctx, 90*time.Second)
	defer cancelGen()
	aktif, err = h.Generator.Generate(genCtx, txtBytes, true)
	if err != nil {
		return nil, nil, err
	}
	semua, err = h.Generator.Generate(genCtx, txtBytes, false)
	if err != nil {
		return nil, nil, err
	}
	return aktif, semua, nil
}

func (h *Slik) currentFolder() string {
	if h.FolderProvider != nil {
		return h.FolderProvider()
	}
	return slik.CurrentFolder()
}

// matchSlikFile cari key pertama yang mengandung query (case-insensitive),
// abaikan file basename dengan prefix "KTP_".
func matchSlikFile(query string, keys []string) string {
	q := strings.ToLower(query)
	for _, k := range keys {
		base := k
		if i := strings.LastIndex(k, "/"); i >= 0 {
			base = k[i+1:]
		}
		if strings.HasPrefix(base, "KTP_") {
			continue
		}
		if strings.Contains(strings.ToLower(k), q) {
			return k
		}
	}
	return ""
}

// folderFromPDFKey extract bagian folder dari key "2026_05/pdf/file.pdf" → "2026_05".
// Kembalikan "" kalau tidak ada segment "/pdf/".
func folderFromPDFKey(key string) string {
	if i := strings.Index(key, "/pdf/"); i > 0 {
		return key[:i]
	}
	return ""
}

// extractSlikDisplayName ambil bagian nama tampilan dari nama file.
//
// Contoh "2026_05/pdf/SMG_2024_budi_santoso.pdf" → "Budi_Santoso".
// Algoritma ikut legacy: split basename (tanpa ekstensi) di "_", skip 2
// segmen pertama (kode kantor + tahun), capitalize huruf pertama setiap
// sisanya, gabung dengan "_".
//
// Fallback kalau cuma <= 2 segmen, return basename tanpa ekstensi apa adanya.
func extractSlikDisplayName(key string) string {
	base := key
	if i := strings.LastIndex(key, "/"); i >= 0 {
		base = key[i+1:]
	}
	base = strings.TrimSuffix(base, ".pdf")
	parts := strings.Split(base, "_")
	if len(parts) <= 2 {
		return base
	}
	out := make([]string, 0, len(parts)-2)
	for _, p := range parts[2:] {
		if p == "" {
			continue
		}
		out = append(out, strings.ToUpper(p[:1])+p[1:])
	}
	return strings.Join(out, "_")
}
