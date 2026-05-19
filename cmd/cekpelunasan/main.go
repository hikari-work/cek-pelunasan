// Package main: entry point cek-pelunasan versi Go.
//
// Bertugas:
//  1. Load config + setup logging
//  2. Connect MongoDB
//  3. Bangun semua repo + service
//  4. Preload AuthorizedChats
//  5. Wire HTTP server (Fiber: miniapp + actuator)
//  6. Wire Telegram bot (commands + callbacks)
//  7. Wire WhatsApp client via whatsmeow (QR pairing kalau belum login)
//  8. Run semuanya dengan graceful shutdown via signal SIGINT/SIGTERM.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/config"
	"github.com/hikari-work/cek-pelunasan/internal/httpserver"
	"github.com/hikari-work/cek-pelunasan/internal/miniapp"
	"github.com/hikari-work/cek-pelunasan/internal/platform/r2"
	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	cbh "github.com/hikari-work/cek-pelunasan/internal/platform/telegram/callbackhandler"
	cmdh "github.com/hikari-work/cek-pelunasan/internal/platform/telegram/commandhandler"
	wa "github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/repository"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/credithistory"
	"github.com/hikari-work/cek-pelunasan/internal/service/kolektas"
	logsvc "github.com/hikari-work/cek-pelunasan/internal/service/log"
	"github.com/hikari-work/cek-pelunasan/internal/service/minbunga"
	"github.com/hikari-work/cek-pelunasan/internal/service/paymentdetails"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
	"github.com/hikari-work/cek-pelunasan/internal/service/slik"
	"github.com/hikari-work/cek-pelunasan/internal/service/sliknotifier"
	"github.com/hikari-work/cek-pelunasan/internal/service/users"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	if err := run(); err != nil && !errors.Is(err, context.Canceled) && !errors.Is(err, http.ErrServerClosed) {
		slog.Error("fatal", "err", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}
	if err := cfg.Validate(); err != nil {
		return fmt.Errorf("invalid config: %w", err)
	}

	slog.Info("cek-pelunasan starting", "version", "5.0.0-go-dev", "port", cfg.Server.Port)

	rootCtx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	connectCtx, connectCancel := context.WithTimeout(rootCtx, 10*time.Second)
	defer connectCancel()
	mongo, err := repository.Connect(connectCtx, cfg.Mongo.URI)
	if err != nil {
		return fmt.Errorf("connect mongo: %w", err)
	}
	defer func() {
		shutdownCtx, c := context.WithTimeout(context.Background(), 5*time.Second)
		defer c()
		_ = mongo.Close(shutdownCtx)
	}()

	usersRepo := repository.NewUserRepo(mongo)
	billsRepo := repository.NewBillsRepo(mongo)
	savingsRepo := repository.NewSavingsRepo(mongo)
	kolekRepo := repository.NewKolekTasRepo(mongo)
	payingRepo := repository.NewPayingRepo(mongo)
	pdRepo := repository.NewPaymentDetailsRepo(mongo)
	chRepo := repository.NewCreditHistoryRepo(mongo)
	dulRepo := repository.NewDataUpdateLogRepo(mongo)
	mbSessRepo := repository.NewMinBungaSessionRepo(mongo)
	slikNotifiedRepo := repository.NewSlikNotifiedFileRepo(mongo)
	_ = payingRepo

	logSvc := logsvc.NewService(dulRepo)
	usersSvc := users.NewService(usersRepo)
	billSvc := bill.NewService(billsRepo, logSvc)
	savingsSvc := savings.NewService(savingsRepo, billsRepo, logSvc)
	kolekSvc := kolektas.NewService(kolekRepo)
	pdSvc := paymentdetails.NewService(pdRepo, logSvc)
	chSvc := credithistory.NewService(chRepo)
	mbSessSvc := minbunga.NewSessionService(mbSessRepo)
	slikSessions := slik.NewSessionCache()
	authedChats := auth.New(usersRepo)

	// R2 client opsional — kalau credential belum diset (mis. dev local), modul SLIK
	// akan fail saat dipakai dengan pesan jelas, tapi bot tetap jalan untuk fitur lain.
	var r2Client *r2.Client
	if cfg.R2.Bucket != "" && cfg.R2.AccessKey != "" && cfg.R2.SecretKey != "" {
		c, err := r2.New(r2.Config{
			AccessKey: cfg.R2.AccessKey,
			AccountID: cfg.R2.AccountID,
			SecretKey: cfg.R2.SecretKey,
			Endpoint:  cfg.R2.Endpoint,
			Bucket:    cfg.R2.Bucket,
		})
		if err != nil {
			slog.Warn("init R2 client failed; SLIK features disabled", "err", err)
		} else {
			r2Client = c
			slog.Info("R2 client initialized", "bucket", cfg.R2.Bucket)
		}
	} else {
		slog.Warn("R2 credentials not set; SLIK features disabled")
	}

	pdfGen := slik.NewPDFGenerator(cfg.SLIK.PDFEndpointURL, cfg.SLIK.PDFLogoURL)
	if cfg.SLIK.SearchTimeout > 0 {
		pdfGen.Timeout = cfg.SLIK.SearchTimeout
	}

	if err := authedChats.Preload(rootCtx); err != nil {
		slog.Warn("preload authorized chats failed", "err", err)
	} else {
		slog.Info("preloaded authorized chats", "count", authedChats.Size())
	}

	// WhatsApp: foundation whatsmeow.
	// Store + client di-init eager. QR pairing dilakukan asynchronous di goroutine
	// supaya bot Telegram + miniapp + actuator tetap bisa start meski user belum
	// scan QR. Fitur WhatsApp baru live setelah handler-nya ditambahkan (task #12+).
	waStore, err := wa.OpenStore(rootCtx, cfg.WhatsApp.DBPath, cfg.WhatsApp.LogLevel)
	if err != nil {
		slog.Warn("whatsapp store gagal dibuka; fitur WA dinonaktifkan", "err", err)
	}
	var waClient *wa.Client
	if waStore != nil {
		defer func() { _ = waStore.Close() }()
		waClient, err = wa.NewClient(waStore, wa.ClientOptions{
			DeviceName: cfg.WhatsApp.DeviceName,
			LogLevel:   cfg.WhatsApp.LogLevel,
		})
		if err != nil {
			slog.Warn("whatsapp client gagal dibuat", "err", err)
		}
	}
	if waClient != nil {
		defer waClient.Close()
	}

	httpApp := httpserver.New(httpserver.Deps{
		MiniApp: miniapp.Deps{
			BotToken:       cfg.Telegram.BotToken,
			SessionTTL:     cfg.MiniApp.SessionTTLMins,
			Auth:           authedChats,
			Users:          usersSvc,
			Bills:          billSvc,
			Savings:        savingsSvc,
			KolekTas:       kolekSvc,
			PaymentDetails: pdSvc,
		},
	})

	api, err := tgbotapi.NewBotAPI(cfg.Telegram.BotToken)
	if err != nil {
		return fmt.Errorf("init telegram bot: %w", err)
	}
	tgBot := telegram.NewBot(api, cfg.Telegram.OwnerID, authedChats)
	tgRouter := telegram.NewRouter()
	registerTelegramHandlers(tgRouter, tgBot, authedChats, usersSvc, billSvc, savingsSvc, kolekSvc, pdSvc, chSvc, mbSessSvc, slikSessions, logSvc, r2Client, pdfGen, cfg.SLIK.MaxPDFSize, cfg.MiniApp.URL)

	if err := telegram.RegisterBotCommands(api, tgRouter.Commands()); err != nil {
		slog.Warn("set bot commands failed", "err", err)
	}

	var wg sync.WaitGroup
	wg.Add(2)
	httpErr := make(chan error, 1)
	tgErr := make(chan error, 1)

	go func() {
		defer wg.Done()
		httpErr <- httpserver.Run(rootCtx, httpApp, fmt.Sprintf(":%d", cfg.Server.Port))
	}()
	go func() {
		defer wg.Done()
		tgErr <- telegram.Run(rootCtx, tgBot, tgRouter)
	}()

	// SLIK notifier hanya jalan kalau R2 siap. Loop berhenti sendiri saat ctx batal.
	if r2Client != nil {
		wg.Add(1)
		notifier := &sliknotifier.Notifier{
			Storage:  r2Client,
			Bot:      tgBot,
			Users:    usersRepo,
			Notified: slikNotifiedRepo,
			Interval: 60 * time.Second,
		}
		go func() {
			defer wg.Done()
			notifier.Run(rootCtx)
		}()
	}

	// WhatsApp: start client di goroutine sendiri.
	// QR pairing (kalau perlu) blocking sampai user scan, jadi tidak boleh
	// menahan main goroutine. Kalau Start gagal, log saja — service lain
	// tetap jalan tanpa fitur WA.
	if waClient != nil {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := waClient.Start(rootCtx); err != nil && !errors.Is(err, context.Canceled) {
				slog.Warn("whatsapp client gagal start", "err", err)
			}
		}()
	}

	select {
	case <-rootCtx.Done():
		slog.Info("shutdown signal received")
	case err := <-httpErr:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("http server stopped", "err", err)
		}
		cancel()
	case err := <-tgErr:
		if err != nil && !errors.Is(err, context.Canceled) {
			slog.Error("telegram bot stopped", "err", err)
		}
		cancel()
	}

	wg.Wait()
	slog.Info("shutdown complete")
	return nil
}

func registerTelegramHandlers(
	r *telegram.Router,
	b *telegram.Bot,
	authed *auth.AuthorizedChats,
	usersSvc *users.Service,
	billSvc *bill.Service,
	savingsSvc *savings.Service,
	kolekSvc *kolektas.Service,
	pdSvc *paymentdetails.Service,
	chSvc *credithistory.Service,
	mbSess *minbunga.SessionService,
	slikSess *slik.SessionCache,
	logSvc *logsvc.Service,
	r2Client *r2.Client,
	pdfGen *slik.PDFGenerator,
	maxPDFSize int64,
	miniAppURL string,
) {
	// Auth middleware tidak diaktifkan global supaya /start /id tetap accessible.
	// Handler yang sensitif pakai role check sendiri di Handle().

	r.RegisterCommand(&cmdh.Start{Authed: authed})
	r.RegisterCommand(&cmdh.ID{})
	r.RegisterCommand(&cmdh.Help{})
	r.RegisterCommand(&cmdh.Auth{OwnerID: b.OwnerID, Authed: authed, Users: usersSvc})
	r.RegisterCommand(&cmdh.Otor{Users: usersSvc, Bills: billSvc})
	r.RegisterCommand(&cmdh.Status{Users: usersSvc, Bills: billSvc, CreditHist: chSvc})
	r.RegisterCommand(&cmdh.Tagih{Bills: billSvc})
	r.RegisterCommand(&cmdh.Dauth{OwnerID: b.OwnerID, Authed: authed, Users: usersSvc})
	r.RegisterCommand(&cmdh.Owner{OwnerID: b.OwnerID, Authed: authed})
	r.RegisterCommand(&cmdh.Kantor{Users: usersSvc})
	r.RegisterCommand(&cmdh.Sim{Bills: billSvc})
	r.RegisterCommand(&cmdh.CariNasabah{Bills: billSvc})
	r.RegisterCommand(&cmdh.Tab{Savings: savingsSvc, Users: usersSvc})
	r.RegisterCommand(&cmdh.Canvas{Savings: savingsSvc})
	r.RegisterCommand(&cmdh.Canvasing{History: chSvc})
	r.RegisterCommand(&cmdh.JatuhBayar{Bills: billSvc, Users: usersSvc})
	r.RegisterCommand(&cmdh.MinimalPay{Bills: billSvc, Users: usersSvc})
	r.RegisterCommand(&cmdh.Kolektas{Service: kolekSvc})
	r.RegisterCommand(&cmdh.Broadcast{OwnerID: b.OwnerID, Users: usersSvc})
	r.RegisterCommand(&cmdh.MinBunga{Users: usersSvc, Bills: billSvc, Sessions: mbSess, Log: logSvc})
	r.RegisterCommand(&cmdh.Slik{Sessions: slikSess})
	r.RegisterCommand(&cmdh.DocSlik{Sessions: slikSess})
	r.RegisterCommand(&cmdh.UploadTagihan{Bills: billSvc})
	r.RegisterCommand(&cmdh.UploadTab{Savings: savingsSvc})
	r.RegisterCommand(&cmdh.UploadTas{Kolektas: kolekSvc})
	r.RegisterCommand(&cmdh.UploadPayment{Payments: pdSvc})
	r.RegisterCommand(&cmdh.UploadCredit{History: chSvc})
	if strings.TrimSpace(miniAppURL) != "" {
		r.RegisterCommand(&cmdh.MiniApp{URL: miniAppURL})
	}

	if r2Client != nil {
		r.RegisterDocumentHandler(&cmdh.SlikDocumentUpload{Authed: authed, Storage: r2Client})
	}

	r.RegisterCallback(&cbh.None{})
	r.RegisterCallback(&cbh.SelectBranch{Bills: billSvc})
	r.RegisterCallback(&cbh.PagingBills{Bills: billSvc})
	r.RegisterCallback(&cbh.Tagihan{Bills: billSvc})
	r.RegisterCallback(&cbh.SavingsBranchPick{Savings: savingsSvc})
	r.RegisterCallback(&cbh.SavingsPaginate{Savings: savingsSvc})
	r.RegisterCallback(&cbh.CanvasPaginate{Savings: savingsSvc})
	r.RegisterCallback(&cbh.CanvasingPaginate{History: chSvc})
	r.RegisterCallback(&cbh.TagihNext{Bills: billSvc, Users: usersSvc})
	r.RegisterCallback(&cbh.MinimalPayPaginate{Bills: billSvc, Users: usersSvc})
	r.RegisterCallback(&cbh.KolektasPaginate{Service: kolekSvc})
	r.RegisterCallback(&cbh.MinBungaBranch{Sessions: mbSess})
	r.RegisterCallback(&cbh.MinBungaCalendarToggle{Sessions: mbSess})
	r.RegisterCallback(&cbh.MinBungaClear{Sessions: mbSess})
	r.RegisterCallback(&cbh.MinBungaConfirm{Sessions: mbSess, Bills: billSvc})
	r.RegisterCallback(&cbh.Services{Bills: billSvc, Savings: savingsSvc})
	r.RegisterCallback(&cbh.SavingsBranchSelect{Savings: savingsSvc})
	r.RegisterCallback(&cbh.SlikMonth{Sessions: slikSess, Storage: r2Client, Users: usersSvc})
	r.RegisterCallback(&cbh.SlikName{Sessions: slikSess})
	r.RegisterCallback(&cbh.SlikSender{Storage: r2Client, Generator: pdfGen, MaxPDFSize: maxPDFSize})
}
