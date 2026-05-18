package utils

import (
	"strings"
	"unicode"
)

// FormatPhoneNumber memetakan nomor mentah dari DB ke tampilan ramah pengguna.
//
//   - Kosong/null -> "📵 Tidak tersedia".
//   - Belum diawali "0" -> ditambahkan otomatis.
//   - Diawali "08" (HP) -> emoji 📱; selainnya (telepon rumah) -> ☎️.
//   - Pola tampilan: XXXX-XXXX-XXXX (atau lebih untuk sisa digit).
func FormatPhoneNumber(phone string) string {
	phone = strings.TrimSpace(phone)
	if phone == "" {
		return "📵 Tidak tersedia"
	}

	if !strings.HasPrefix(phone, "0") {
		phone = "0" + phone
	}

	icon := "☎️"
	if strings.HasPrefix(phone, "08") {
		icon = "📱"
	}

	return icon + " " + groupDigitsForPhone(phone)
}

func groupDigitsForPhone(phone string) string {
	digits := make([]rune, 0, len(phone))
	for _, r := range phone {
		if unicode.IsDigit(r) {
			digits = append(digits, r)
		}
	}
	if len(digits) <= 4 {
		return string(digits)
	}
	if len(digits) <= 8 {
		return string(digits[:4]) + "-" + string(digits[4:])
	}
	return string(digits[:4]) + "-" + string(digits[4:8]) + "-" + string(digits[8:])
}
