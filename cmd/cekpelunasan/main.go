// Package main: entry point cek-pelunasan versi Go.
//
// Bertugas:
//  1. Load config + setup logging
//  2. Connect MongoDB
//  3. Bangun semua repo + service
//  4. Preload AuthorizedChats
//  5. Wire HTTP server (Fiber: miniapp + whatsapp webhook + actuator)
//  6. Wire Telegram bot (commands + callbacks)
//  7. Run keduanya dengan graceful shutdown via signal SIGINT/SIGTERM.
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
	"github.com/hikari-work/cek-pelunasan/internal/service/paymentdetails"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
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
	_ = repository.NewMinBungaSessionRepo(mongo)
	_ = repository.NewSlikNotifiedFileRepo(mongo)
	_ = payingRepo

	logSvc := logsvc.NewService(dulRepo)
	usersSvc := users.NewService(usersRepo)
	billSvc := bill.NewService(billsRepo, logSvc)
	savingsSvc := savings.NewService(savingsRepo, billsRepo, logSvc)
	kolekSvc := kolektas.NewService(kolekRepo)
	pdSvc := paymentdetails.NewService(pdRepo, logSvc)
	chSvc := credithistory.NewService(chRepo)
	authedChats := auth.New(usersRepo)

	if err := authedChats.Preload(rootCtx); err != nil {
		slog.Warn("preload authorized chats failed", "err", err)
	} else {
		slog.Info("preloaded authorized chats", "count", authedChats.Size())
	}

	wsSender := wa.NewSender(cfg.WhatsApp.GatewayURL, cfg.WhatsApp.GatewayUsername, cfg.WhatsApp.GatewayPassword)
	wsRouter := wa.NewRouter(cfg.WhatsApp.AdminNumber)
	registerWhatsAppHandlers(wsRouter, wsSender)

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
		WhatsAppRouter: wsRouter,
	})

	api, err := tgbotapi.NewBotAPI(cfg.Telegram.BotToken)
	if err != nil {
		return fmt.Errorf("init telegram bot: %w", err)
	}
	tgBot := telegram.NewBot(api, cfg.Telegram.OwnerID, authedChats)
	tgRouter := telegram.NewRouter()
	registerTelegramHandlers(tgRouter, tgBot, authedChats, usersSvc, billSvc, chSvc, cfg.MiniApp.URL)

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

func registerWhatsAppHandlers(r *wa.Router, sender *wa.Sender) {
	r.Add(wa.PendingHotKolek(sender))
	r.Add(wa.PendingCommand(".p", "cek pelunasan", sender))
	r.Add(wa.PendingCommand(".t", "tabungan", sender))
	r.Add(wa.PendingCommand(".slik", "SLIK", sender))
	r.Add(wa.PendingCommand(".va", "virtual account", sender))
	r.Add(wa.PendingCommand(".jb", "jatuh bayar", sender))
	r.Add(wa.PendingCommand(".minbunga", "minimal bunga", sender))
	r.Add(wa.PendingCommand(".email", "email forward", sender))
}

func registerTelegramHandlers(
	r *telegram.Router,
	b *telegram.Bot,
	authed *auth.AuthorizedChats,
	usersSvc *users.Service,
	billSvc *bill.Service,
	chSvc *credithistory.Service,
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
	if strings.TrimSpace(miniAppURL) != "" {
		r.RegisterCommand(&cmdh.MiniApp{URL: miniAppURL})
	}

	pending := []struct{ cmd, desc string }{
		{"/tagih", "Cari tagihan AO"},
		{"/cariNasabah", "Cari nasabah"},
		{"/canvas", "Canvasing tabungan"},
		{"/canvasingTab", "Canvasing tabungan (tab)"},
		{"/cariTab", "Cari tabungan"},
		{"/kolektas", "Kolek Tas (kelompok)"},
		{"/kantor", "Daftar kantor"},
		{"/findbyduedate", "Cari by jatuh tempo"},
		{"/minimalpay", "Tagihan minimal pay"},
		{"/minbunga", "Tagihan minimal bunga"},
		{"/slik", "SLIK"},
		{"/docslik", "Dokumen SLIK"},
		{"/uploadbills", "Upload data tagihan"},
		{"/uploadtab", "Upload data tabungan"},
		{"/uploadtas", "Upload Kolek Tas"},
		{"/uploadpayment", "Upload payment details"},
		{"/uploadhistory", "Upload credit history"},
		{"/broadcast", "Broadcast pesan"},
		{"/dauth", "Hapus user"},
		{"/owner", "Hubungi owner"},
		{"/sim", "Simulasi angsuran"},
	}
	for _, p := range pending {
		r.RegisterCommand(cmdh.Pending(p.cmd, p.desc))
	}

	r.RegisterCallback(&cbh.None{})
	for _, prefix := range []string{
		"tagihan", "namaTagihan", "branch", "savingsBranch", "savingsNext",
		"canvas", "canvasingTab", "kolektas", "minbunga", "minbungaCal",
		"minbungaClear", "minbungaConfirm", "minimalpay", "slikMonth",
		"slikName", "slikSender", "tagihNext", "savingsBranchSelect", "services",
	} {
		r.RegisterCallback(cbh.Pending(prefix))
	}
}
