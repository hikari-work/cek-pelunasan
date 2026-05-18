package config

import (
	"testing"
)

func TestLoad_DefaultsApplied(t *testing.T) {
	t.Setenv("TELEGRAM_BOT_TOKEN", "dummy")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.Mongo.URI != "mongodb://localhost:27017/cek_pelunasan" {
		t.Errorf("default Mongo.URI = %q", cfg.Mongo.URI)
	}
	if cfg.Server.Port != 8080 {
		t.Errorf("default Server.Port = %d", cfg.Server.Port)
	}
	if cfg.SLIK.MaxResults != 50 {
		t.Errorf("default SLIK.MaxResults = %d", cfg.SLIK.MaxResults)
	}
	if err := cfg.Validate(); err != nil {
		t.Errorf("Validate() = %v", err)
	}
}

func TestLoad_OverridesFromEnv(t *testing.T) {
	t.Setenv("TELEGRAM_BOT_TOKEN", "abc")
	t.Setenv("TELEGRAM_BOT_OWNER", "12345")
	t.Setenv("SERVER_PORT", "9090")
	t.Setenv("SLIK_MAX_RESULTS", "100")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.Telegram.OwnerID != 12345 {
		t.Errorf("Telegram.OwnerID = %d", cfg.Telegram.OwnerID)
	}
	if cfg.Server.Port != 9090 {
		t.Errorf("Server.Port = %d", cfg.Server.Port)
	}
	if cfg.SLIK.MaxResults != 100 {
		t.Errorf("SLIK.MaxResults = %d", cfg.SLIK.MaxResults)
	}
}

func TestValidate_RequiresBotToken(t *testing.T) {
	cfg := &Config{}
	if err := cfg.Validate(); err == nil {
		t.Error("expected error for missing TELEGRAM_BOT_TOKEN")
	}
}

func TestLoad_InvalidIntFails(t *testing.T) {
	t.Setenv("SERVER_PORT", "not-a-number")
	if _, err := Load(); err == nil {
		t.Error("expected error for invalid SERVER_PORT")
	}
}
