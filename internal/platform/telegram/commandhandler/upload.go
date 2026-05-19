package commandhandler

import (
	"context"
	"fmt"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/credithistory"
	"github.com/hikari-work/cek-pelunasan/internal/service/csvimport"
	"github.com/hikari-work/cek-pelunasan/internal/service/kolektas"
	"github.com/hikari-work/cek-pelunasan/internal/service/paymentdetails"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// importer signature umum semua service yang punya ParseCSVAndSave.
type importer func(ctx context.Context, path string, total int64, onProgress csvimport.ProgressFn) error

// runUpload jalankan flow upload generik:
//  1. ambil URL dari pesan,
//  2. download CSV,
//  3. kirim progress,
//  4. eksekusi importer,
//  5. report sukses/gagal.
//
// Pesan progress dijalankan async dari goroutine importer; itu sebabnya callback
// progress tidak boleh blocking.
func runUpload(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message,
	cmd, label string, run importer,
) {
	chatID := msg.Chat.ID
	url := utils.ExtractURL(msg.Text)
	if url == "" {
		_, _ = b.SendText(chatID,
			fmt.Sprintf("❗ *Format salah.*\nGunakan `%s <link_csv>`", cmd))
		return
	}
	path, err := utils.DownloadCSV(url)
	if err != nil {
		_, _ = b.SendText(chatID, "❌ Gagal mengunduh: "+err.Error())
		return
	}
	total := utils.CountCSVDataLines(path)
	rep := telegram.NewProgressReporter(b, chatID, label, total)
	err = run(ctx, path, total, func(processed int64) { rep.Update(processed) })
	if err != nil {
		rep.Finish("❌ Gagal memproses file: " + err.Error())
		return
	}
	rep.Finish(fmt.Sprintf("✅ %s berhasil diimpor (%d baris)", label, total))
}

// UploadTagihan /uploadtagihan — admin-only.
type UploadTagihan struct{ Bills *bill.Service }

func (h *UploadTagihan) Command() string     { return "/uploadtagihan" }
func (h *UploadTagihan) Description() string { return "Upload data tagihan dari URL CSV" }
func (h *UploadTagihan) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	runUpload(ctx, b, msg, h.Command(), "Data Tagihan", h.Bills.ParseCSVAndSave)
}

// UploadTab /uploadtab — admin-only.
type UploadTab struct{ Savings *savings.Service }

func (h *UploadTab) Command() string     { return "/uploadtab" }
func (h *UploadTab) Description() string { return "Upload data tabungan dari URL CSV" }
func (h *UploadTab) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	runUpload(ctx, b, msg, h.Command(), "Data Tabungan", h.Savings.ParseCSVAndSave)
}

// UploadTas /uploadtas — admin-only.
type UploadTas struct{ Kolektas *kolektas.Service }

func (h *UploadTas) Command() string     { return "/uploadtas" }
func (h *UploadTas) Description() string { return "Upload data Kolek Tas dari URL CSV" }
func (h *UploadTas) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	runUpload(ctx, b, msg, h.Command(), "Kolek Tas", h.Kolektas.ParseCSVAndSave)
}

// UploadPayment /uploadpayment — admin-only.
type UploadPayment struct{ Payments *paymentdetails.Service }

func (h *UploadPayment) Command() string     { return "/uploadpayment" }
func (h *UploadPayment) Description() string { return "Upload data payment details dari URL CSV" }
func (h *UploadPayment) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	runUpload(ctx, b, msg, h.Command(), "Payment Details", h.Payments.ParseCSVAndSave)
}

// UploadCredit /uploadcredit — admin-only.
type UploadCredit struct{ History *credithistory.Service }

func (h *UploadCredit) Command() string     { return "/uploadcredit" }
func (h *UploadCredit) Description() string { return "Upload data riwayat kredit dari URL CSV" }
func (h *UploadCredit) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	runUpload(ctx, b, msg, h.Command(), "Riwayat Kredit", h.History.ParseCSVAndSave)
}
