package whatsapp

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	waLog "go.mau.fi/whatsmeow/util/log"

	_ "modernc.org/sqlite" // pure-Go SQLite driver — no CGO
)

// Store membungkus *sqlstore.Container + device row yang dipakai client.
//
// Container memegang koneksi SQL bersama (Signal protocol keys, app state,
// dll). Device adalah row khusus untuk satu account WhatsApp; saat first-run
// row-nya kosong dan client akan minta QR pairing.
type Store struct {
	Container *sqlstore.Container
	Device    *store.Device
}

// OpenStore membuka SQLite di path yang ditentukan, jalankan migrasi,
// lalu return device pertama (atau bikin baru kalau belum ada).
//
// dbPath bisa berupa file path biasa (mis. "./data/wa.db") — direktori
// induknya akan dibuat otomatis kalau belum ada. Untuk tuning lanjut,
// caller bisa kasih DSN eksplisit yang dimulai dengan "file:".
func OpenStore(ctx context.Context, dbPath, logLevel string) (*Store, error) {
	dsn, err := resolveDSN(dbPath)
	if err != nil {
		return nil, err
	}

	dbLog := waLog.Stdout("WA-DB", normalizeLogLevel(logLevel), true)

	container, err := sqlstore.New(ctx, "sqlite", dsn, dbLog)
	if err != nil {
		return nil, fmt.Errorf("open whatsmeow store: %w", err)
	}

	device, err := container.GetFirstDevice(ctx)
	if err != nil {
		_ = container.Close()
		return nil, fmt.Errorf("get device from store: %w", err)
	}

	return &Store{Container: container, Device: device}, nil
}

// Close tutup koneksi SQL underlying. Aman dipanggil dua kali.
func (s *Store) Close() error {
	if s == nil || s.Container == nil {
		return nil
	}
	return s.Container.Close()
}

// IsLoggedIn true kalau device sudah pernah pair dengan WhatsApp.
// Saat false, caller harus jalankan QR flow sebelum Connect.
func (s *Store) IsLoggedIn() bool {
	return s != nil && s.Device != nil && s.Device.ID != nil
}

// resolveDSN: terima file path biasa atau DSN sqlite eksplisit.
// Kalau file path, pastikan parent directory ada dan tambah pragma
// foreign_keys + busy_timeout supaya aman dari "database is locked"
// kalau ada concurrent writer (contoh: backup script).
func resolveDSN(input string) (string, error) {
	input = strings.TrimSpace(input)
	if input == "" {
		return "", fmt.Errorf("WA_DB_PATH kosong")
	}
	if strings.HasPrefix(input, "file:") {
		return input, nil
	}
	if dir := filepath.Dir(input); dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return "", fmt.Errorf("create db dir %q: %w", dir, err)
		}
	}
	return "file:" + input + "?_pragma=foreign_keys(1)&_pragma=busy_timeout(5000)", nil
}

func normalizeLogLevel(level string) string {
	switch strings.ToUpper(strings.TrimSpace(level)) {
	case "DEBUG", "INFO", "WARN", "ERROR":
		return strings.ToUpper(level)
	case "":
		return "INFO"
	default:
		return "INFO"
	}
}
