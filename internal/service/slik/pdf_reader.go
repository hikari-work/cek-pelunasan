package slik

import (
	"bytes"
	"errors"
	"regexp"

	"github.com/ledongthuc/pdf"
)

var nikRe = regexp.MustCompile(`\b\d{16}\b`)

// ExtractNIK baca PDF lalu extract pola 16-digit pertama (NIK KTP).
// Mengembalikan string kosong kalau tidak ditemukan; error kalau parse gagal.
func ExtractNIK(data []byte) (string, error) {
	if len(data) == 0 {
		return "", errors.New("empty pdf bytes")
	}
	r, err := pdf.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return "", err
	}
	var buf bytes.Buffer
	for i := 1; i <= r.NumPage(); i++ {
		page := r.Page(i)
		if page.V.IsNull() {
			continue
		}
		text, err := page.GetPlainText(nil)
		if err != nil {
			continue
		}
		buf.WriteString(text)
		// shortcut: cek setelah tiap halaman, kalau sudah ketemu langsung balik.
		if m := nikRe.FindString(buf.String()); m != "" {
			return m, nil
		}
	}
	if m := nikRe.FindString(buf.String()); m != "" {
		return m, nil
	}
	return "", nil
}
