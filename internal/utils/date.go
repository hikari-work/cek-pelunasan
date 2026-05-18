package utils

import "time"

// DayOfMonth mengembalikan tanggal (hari dalam bulan) sebagai string dua digit.
// Padanan dari DateUtils.converterDate yang lama.
func DayOfMonth(t time.Time) string {
	return t.Format("02")
}
