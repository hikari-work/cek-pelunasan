package utils

import (
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const csvDownloadDir = "files"

// ExtractURL mengambil URL dari teks command bentuk `/cmd <url>`.
// Mengembalikan "" kalau argumen tidak ada.
func ExtractURL(text string) string {
	parts := strings.SplitN(text, " ", 2)
	if len(parts) < 2 {
		return ""
	}
	return strings.TrimSpace(parts[1])
}

// ExtractFileName mengambil nama file dari URL — bagian setelah slash terakhir.
func ExtractFileName(fileURL string) string {
	idx := strings.LastIndex(fileURL, "/")
	if idx < 0 || idx == len(fileURL)-1 {
		return fileURL
	}
	return fileURL[idx+1:]
}

// DownloadCSV mengunduh file CSV ke folder ./files dan mengembalikan path lokal.
// Hanya menerima URL yang berakhir .csv (case-insensitive) supaya tidak mudah salah pakai.
func DownloadCSV(fileURL string) (string, error) {
	parsed, err := url.Parse(fileURL)
	if err != nil {
		return "", fmt.Errorf("url tidak valid: %w", err)
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return "", errors.New("url harus http(s)")
	}
	name := ExtractFileName(fileURL)
	if !strings.HasSuffix(strings.ToLower(name), ".csv") {
		return "", fmt.Errorf("file bukan CSV: %s", name)
	}
	if err := os.MkdirAll(csvDownloadDir, 0o755); err != nil {
		return "", fmt.Errorf("buat direktori: %w", err)
	}
	dst := filepath.Join(csvDownloadDir, name)

	client := &http.Client{Timeout: 10 * time.Minute}
	resp, err := client.Get(fileURL)
	if err != nil {
		return "", fmt.Errorf("download: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("status %d dari %s", resp.StatusCode, fileURL)
	}

	out, err := os.Create(dst)
	if err != nil {
		return "", fmt.Errorf("buka file tujuan: %w", err)
	}
	defer out.Close()
	if _, err := io.Copy(out, resp.Body); err != nil {
		return "", fmt.Errorf("simpan file: %w", err)
	}
	return dst, nil
}

// CountCSVDataLines hitung baris non-header dengan stream byte. Tidak parse CSV.
// Mengembalikan 0 saat file tidak bisa dibuka.
func CountCSVDataLines(path string) int64 {
	f, err := os.Open(path)
	if err != nil {
		return 0
	}
	defer f.Close()
	var (
		buf   = make([]byte, 64*1024)
		count int64
	)
	for {
		n, err := f.Read(buf)
		for i := 0; i < n; i++ {
			if buf[i] == '\n' {
				count++
			}
		}
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return 0
		}
	}
	if count > 0 {
		return count - 1
	}
	return 0
}
