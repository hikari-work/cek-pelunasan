// Package httpserver: Fiber app yang mounting mini app + WhatsApp webhook +
// /actuator equivalent (health, info, prometheus).
package httpserver

import (
	"context"
	"log/slog"
	"runtime"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/adaptor"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/hikari-work/cek-pelunasan/internal/miniapp"
	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
)

type Deps struct {
	MiniApp        miniapp.Deps
	WhatsAppRouter *whatsapp.Router
}

var startTime = time.Now()

// New bikin Fiber app yang sudah di-wire dengan semua endpoint.
//
// Layout endpoint:
//   - /api/mini/*       -> miniapp
//   - /v2/whatsapp      -> WhatsApp webhook
//   - /actuator/health  -> simple health (UP + uptime)
//   - /actuator/info    -> app + runtime info
//   - /actuator/prometheus -> Go runtime + process metrics
//   - /                 -> static dari web/static
func New(d Deps) *fiber.App {
	app := fiber.New(fiber.Config{
		AppName:               "cek-pelunasan-go",
		DisableStartupMessage: true,
		ReadTimeout:           60 * time.Second,
		WriteTimeout:          60 * time.Second,
	})

	app.Use(recover.New())
	app.Use(cors.New(cors.Config{
		AllowOrigins: "*",
		AllowMethods: "GET,POST,PUT,PATCH,DELETE,OPTIONS",
		AllowHeaders: "Origin,Content-Type,Accept,Authorization,X-Mini-Token",
	}))

	miniapp.Register(app, d.MiniApp)
	whatsapp.MountWebhook(app, d.WhatsAppRouter)

	app.Get("/actuator/health", health)
	app.Get("/actuator/info", info)
	registerMetrics(app)

	app.Static("/", "./web/static")

	return app
}

func health(c *fiber.Ctx) error {
	return c.JSON(fiber.Map{
		"status": "UP",
		"uptime": time.Since(startTime).String(),
	})
}

func info(c *fiber.Ctx) error {
	return c.JSON(fiber.Map{
		"app": fiber.Map{
			"name":    "cek-pelunasan",
			"version": "5.0.0-go-dev",
		},
		"runtime": fiber.Map{
			"go":         runtime.Version(),
			"goroutines": runtime.NumGoroutine(),
		},
	})
}

// registerMetrics expose default Go runtime + process metrics di
// /actuator/prometheus. Promhttp adalah net/http handler — adaptor middleware
// menjembatani ke Fiber.
func registerMetrics(app *fiber.App) {
	reg := prometheus.NewRegistry()
	reg.MustRegister(prometheus.NewGoCollector())
	reg.MustRegister(prometheus.NewProcessCollector(prometheus.ProcessCollectorOpts{}))
	app.Get("/actuator/prometheus", adaptor.HTTPHandler(promhttp.HandlerFor(reg, promhttp.HandlerOpts{Registry: reg})))
}

// Run start Fiber server, blok sampai ctx done atau Listen error.
// Saat ctx done, panggil ShutdownWithContext (timeout 10s).
func Run(ctx context.Context, app *fiber.App, addr string) error {
	errCh := make(chan error, 1)
	go func() {
		slog.Info("http server listening", "addr", addr)
		errCh <- app.Listen(addr)
	}()
	select {
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := app.ShutdownWithContext(shutdownCtx); err != nil {
			slog.Warn("http server shutdown error", "err", err)
		}
		return ctx.Err()
	case err := <-errCh:
		return err
	}
}
