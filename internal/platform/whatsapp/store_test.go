package whatsapp

import (
	"context"
	"path/filepath"
	"strings"
	"testing"
)

func TestNormalizeLogLevel(t *testing.T) {
	cases := map[string]string{
		"":      "INFO",
		"info":  "INFO",
		"INFO":  "INFO",
		"debug": "DEBUG",
		"WARN":  "WARN",
		"error": "ERROR",
		"trace": "INFO", // unknown → fallback INFO
	}
	for in, want := range cases {
		if got := normalizeLogLevel(in); got != want {
			t.Errorf("normalizeLogLevel(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestResolveDSN_FilePathCreatesDir(t *testing.T) {
	dir := t.TempDir()
	dbPath := filepath.Join(dir, "nested", "wa.db")

	dsn, err := resolveDSN(dbPath)
	if err != nil {
		t.Fatalf("resolveDSN error: %v", err)
	}
	if !strings.HasPrefix(dsn, "file:") {
		t.Errorf("DSN should start with file:, got %q", dsn)
	}
	if !strings.Contains(dsn, "foreign_keys(1)") {
		t.Errorf("DSN should enable foreign_keys, got %q", dsn)
	}
	if !strings.Contains(dsn, "busy_timeout(5000)") {
		t.Errorf("DSN should set busy_timeout, got %q", dsn)
	}
}

func TestResolveDSN_PassThroughExplicitDSN(t *testing.T) {
	in := "file:custom.db?_pragma=journal_mode(WAL)"
	got, err := resolveDSN(in)
	if err != nil {
		t.Fatalf("resolveDSN error: %v", err)
	}
	if got != in {
		t.Errorf("resolveDSN(%q) altered explicit DSN: %q", in, got)
	}
}

func TestResolveDSN_RejectsEmpty(t *testing.T) {
	if _, err := resolveDSN("  "); err == nil {
		t.Error("expected error for empty path")
	}
}

// TestOpenStore_FreshDatabase: smoke test bahwa SQLite driver kebawa, migrasi
// jalan, dan device kosong di-create otomatis (ID == nil = belum pair).
func TestOpenStore_FreshDatabase(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "wa.db")

	store, err := OpenStore(context.Background(), dbPath, "ERROR")
	if err != nil {
		t.Fatalf("OpenStore error: %v", err)
	}
	t.Cleanup(func() { _ = store.Close() })

	if store.Device == nil {
		t.Fatal("Device nil setelah OpenStore")
	}
	if store.IsLoggedIn() {
		t.Error("fresh store seharusnya belum logged in")
	}
}
