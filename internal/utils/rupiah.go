// Package utils berisi helper generik (formatter angka, telepon, dsb.)
// yang tidak terikat ke domain spesifik. Helper domain-spesifik
// (TagihanUtils, MinBungaMessageFormatter, dst.) ada di package service masing-masing.
package utils

import (
	"fmt"
	"strings"
)

// FormatRupiah mengubah nominal int64 ke "Rp1.500.000".
// Pemisah ribuan: titik, sesuai standar Indonesia.
func FormatRupiah(amount int64) string {
	if amount == 0 {
		return "Rp0"
	}
	negative := amount < 0
	if negative {
		amount = -amount
	}
	s := fmt.Sprintf("%d", amount)
	var b strings.Builder
	n := len(s)
	for i, c := range s {
		if i > 0 && (n-i)%3 == 0 {
			b.WriteByte('.')
		}
		b.WriteRune(c)
	}
	if negative {
		return "-Rp" + b.String()
	}
	return "Rp" + b.String()
}
