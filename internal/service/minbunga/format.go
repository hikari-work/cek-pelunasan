package minbunga

import (
	"fmt"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/utils"
)

// MaxMessageChars batas char per pesan supaya tidak melebihi limit Telegram.
// WhatsApp limit-nya jauh lebih besar; pakai angka yang sama agar konsisten.
const MaxMessageChars = 3800

// FormatMessages chunk hasil per kelompok tanggal supaya tidak melebihi
// MaxMessageChars per pesan. Padanan MinBungaMessageFormatter.format dari legacy.
//
// Output bisa banyak pesan: satu kelompok tanggal mungkin perlu di-split
// jika daftar tagihannya panjang.
func FormatMessages(groups []BillsForDate, identifier string) []string {
	var messages []string

	for _, g := range groups {
		header := buildHeader(g.TargetDate, g.DaysDiff, identifier, len(g.Bills))
		current := header
		for _, db := range g.Bills {
			entryStr := buildEntry(db, g.TargetDate, g.DaysDiff)
			if len(current)+len(entryStr) > MaxMessageChars {
				messages = append(messages, current)
				current = "_Lanjutan " + formatTanggalID(g.TargetDate) + "_\n\n"
			}
			current += entryStr
		}
		if current != "" {
			messages = append(messages, current)
		}
	}

	if len(messages) == 0 {
		messages = append(messages,
			"*Tidak ada tagihan yang memenuhi kriteria.*\n_Semua nasabah masih aman dalam batas DayLate 90 hari._")
	}
	return messages
}

func buildHeader(date time.Time, daysDiff int, identifier string, count int) string {
	return "*Tagihan: " + formatTanggalID(date) + "* (+" + fmt.Sprintf("%d", daysDiff) + " hari)\n" +
		"Minimal bayar Maksimal di: " + formatTanggalDayID(date) + "\n" +
		"ID: " + identifier + " | Jumlah: " + fmt.Sprintf("%d", count) + " tagihan\n" +
		"─────────────────────\n\n"
}

func buildEntry(db DatedBill, targetDate time.Time, daysDiff int) string {
	bill := db.Bill
	simulatedDayLate := db.DayLate + daysDiff
	threshold := 90 - simulatedDayLate
	maksBayar := targetDate.AddDate(0, 0, threshold)
	jikaNotPay := bill.LastPrincipal + bill.Principal + bill.MinInterest

	return "*" + bill.Name + "*\n" +
		"Alamat: " + bill.Address + "\n" +
		"AO: " + bill.AccountOfficer + "\n\n" +
		"Plafond: " + utils.FormatRupiah(bill.Plafond) + "\n" +
		"Baki Debet: " + utils.FormatRupiah(bill.DebitTray) + "\n" +
		"Tgg. Pokok: " + utils.FormatRupiah(bill.LastPrincipal) + "\n" +
		"Tgg. Bunga: " + utils.FormatRupiah(bill.LastInterest) + "\n" +
		"Min. Pokok: " + utils.FormatRupiah(bill.MinPrincipal) + "\n" +
		"Min. Bunga: " + utils.FormatRupiah(bill.MinInterest) + "\n\n" +
		"Maks. Bayar: " + formatTanggalDayID(maksBayar) + "\n" +
		"Jika Tdk Bayar: " + utils.FormatRupiah(jikaNotPay) + "\n" +
		"─────────────────────\n\n"
}

func formatTanggalID(t time.Time) string {
	bulan := []string{"Januari", "Februari", "Maret", "April", "Mei", "Juni",
		"Juli", "Agustus", "September", "Oktober", "November", "Desember"}
	return fmt.Sprintf("%d %s %d", t.Day(), bulan[int(t.Month())-1], t.Year())
}

func formatTanggalDayID(t time.Time) string {
	hari := []string{"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"}
	return hari[t.Weekday()] + ", " + formatTanggalID(t)
}
