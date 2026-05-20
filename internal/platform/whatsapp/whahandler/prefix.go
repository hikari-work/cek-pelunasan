package whahandler

import "strings"

// defaultPrefix dipakai kalau handler tidak terkonfigurasi (Prefix kosong).
// Mempertahankan perilaku awal "." supaya test fleksibel — produksi selalu
// inject lewat env WA_COMMAND_PREFIX.
const defaultPrefix = "."

// prefixed bangun token command lengkap, mis. prefix="." + name="p" → ".p".
// Pakai defaultPrefix kalau p kosong.
func prefixed(p, name string) string {
	if p == "" {
		p = defaultPrefix
	}
	return p + name
}

// matchCommand true kalau body == cmd ATAU body diawali "cmd " (ada argumen
// tambahan setelah spasi). Body sudah harus di-trim caller.
func matchCommand(body, cmd string) bool {
	if body == cmd {
		return true
	}
	return strings.HasPrefix(body, cmd+" ")
}
