package main

import (
	"log/slog"
	"os"

	"github.com/hikari-work/cek-pelunasan/internal/config"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	cfg, err := config.Load()
	if err != nil {
		slog.Error("load config", "err", err)
		os.Exit(1)
	}
	if err := cfg.Validate(); err != nil {
		slog.Error("invalid config", "err", err)
		os.Exit(1)
	}

	slog.Info("cek-pelunasan starting",
		"version", "5.0.0-go-dev",
		"server_port", cfg.Server.Port,
		"mongo_uri_set", cfg.Mongo.URI != "",
	)
}
