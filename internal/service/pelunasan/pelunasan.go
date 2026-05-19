// Package pelunasan menghitung estimasi pelunasan kredit dari data Bills.
//
// Logikanya membedakan dua jenis kredit berdasarkan 2 karakter terakhir
// kode produk:
//
//   - LM (Flat Murni): penalty bertingkat berdasarkan umur pinjaman.
//     Kalau periode total > 12 bulan: cap penalty 6x sebelum 12 bulan
//     berjalan, 3x setelahnya. Kalau periode <= 12 bulan: cap 3x kalau
//     baru < 6 bulan, 1x kalau sudah > 6 bulan.
//   - DG (Anuitas): penalty 1x bunga tetap, kecuali kalau jatuh tempo
//     bulan yang sama dengan bulan pelunasan (penalty = 0).
//
// Bunga yang dihitung mempertimbangkan apakah tanggal realisasi sudah
// lewat hari ini di bulan ini atau belum — kalau belum, bunga berjalan
// (Interest) ditambahkan ke base (LastInterest - Titipan).
package pelunasan

import (
	"errors"
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

// Wajakarta zone: WIB +07:00 fixed, sama dengan service lain.
var jakartaTZ = time.FixedZone("WIB", 7*3600)

const (
	flatMurniType = "LM"
	anuitasType   = "DG"

	flatMurniMaxLongTerm  = 6
	flatMurniMaxShortTerm = 3
	flatMurniMin          = 1
	longTermMonths        = 12
	shortTermThreshold    = 6

	tunggakanBunga = "Tunggakan Bunga"
	titipanBunga   = "Titipan Bunga"
	bungaLabel     = "Bunga"
)

// Result adalah hasil perhitungan yang siap diformat ke pesan/JSON.
//
// Struktur dipertahankan sedekat mungkin dengan PelunasanDto Java supaya
// konsumer (handler WhatsApp + miniapp /pelunasan/:spk) bisa dipakai
// sama-sama tanpa konversi tambahan.
type Result struct {
	Nama              string
	SPK               string
	Alamat            string
	Plafond           int64
	BakiDebet         int64
	TglRealisasi      string
	TglJatuhTempo     string
	RencanaPelunasan  string
	PerhitunganBunga  int64
	Penalty           int64
	MultiplierPenalty int
	Denda             int64
	TypeBunga         string
}

// TotalPelunasan jumlah yang harus dibayar nasabah saat pelunasan:
// baki debet + bunga + penalty + denda.
func (r *Result) TotalPelunasan() int64 {
	return r.BakiDebet + r.PerhitunganBunga + r.Penalty + r.Denda
}

// Calculate hitung pelunasan dari data Bills.
//
// Error dikembalikan kalau Bills nil, Product < 2 char, atau tanggal
// realisasi/jatuh tempo tidak valid (format YYYY-MM-DD).
func Calculate(b *entity.Bills) (*Result, error) {
	return calculateAt(b, time.Now().In(jakartaTZ))
}

// calculateAt versi inject "today" untuk testing — supaya test tidak
// flaky tergantung tanggal sistem.
func calculateAt(b *entity.Bills, today time.Time) (*Result, error) {
	if b == nil {
		return nil, errors.New("bills is nil")
	}
	if len(b.Product) < 2 {
		return nil, errors.New("product type minimal 2 karakter")
	}
	if b.Realization == "" || b.DueDate == "" {
		return nil, errors.New("realisasi dan jatuh tempo wajib")
	}

	realization, err := parseDate(b.Realization)
	if err != nil {
		return nil, err
	}
	dueDate, err := parseDate(b.DueDate)
	if err != nil {
		return nil, err
	}

	creditType := strings.ToUpper(b.Product[len(b.Product)-2:])
	multiplier := penaltyMultiplier(creditType, realization, dueDate, today)

	denda := b.PenaltyInterest + b.PenaltyPrincipal
	penalty := b.FixedInterest * int64(multiplier)

	bunga, tipeBunga := calcInterest(b, realization, today)

	return &Result{
		Nama:              b.Name,
		SPK:               b.NoSpk,
		Alamat:            b.Address,
		Plafond:           b.Plafond,
		BakiDebet:         b.DebitTray,
		TglRealisasi:      b.Realization,
		TglJatuhTempo:     b.DueDate,
		RencanaPelunasan:  today.Format("2006-01-02"),
		PerhitunganBunga:  bunga,
		Penalty:           penalty,
		MultiplierPenalty: multiplier,
		Denda:             denda,
		TypeBunga:         tipeBunga,
	}, nil
}

func penaltyMultiplier(creditType string, realization, dueDate, today time.Time) int {
	switch creditType {
	case flatMurniType:
		// Kalau jatuh tempo bulan dan tahun yang sama dengan hari ini → tidak ada penalty.
		if sameMonthYear(dueDate, today) {
			return 0
		}
		monthsPassed := monthsBetween(realization, today)
		totalMonths := monthsBetween(realization, dueDate)
		monthsLeft := totalMonths - monthsPassed
		if totalMonths > longTermMonths {
			if monthsPassed < longTermMonths {
				return min(flatMurniMaxLongTerm, monthsLeft)
			}
			return min(flatMurniMaxShortTerm, monthsLeft)
		}
		if monthsPassed > shortTermThreshold {
			return min(flatMurniMin, monthsLeft)
		}
		return min(flatMurniMaxShortTerm, monthsLeft)

	case anuitasType:
		if sameMonthYear(today, dueDate) {
			return 0
		}
		return 1
	}
	// Tipe lain: tidak dikenali → multiplier 0 (tidak ada penalty).
	return 0
}

// calcInterest hitung jumlah bunga + tipe yang ditampilkan ke user.
//
// Formula:
//
//	base = LastInterest - Titipan
//	kalau hari realisasi > hari ini di bulan ini → tambahkan Interest berjalan
//
// Tipe ditentukan dari sign akhir:
//
//	< 0 → "Titipan Bunga" (user lebih bayar)
//	> 0 → "Tunggakan Bunga"
//	= 0 → "Bunga"
func calcInterest(b *entity.Bills, realization, today time.Time) (amount int64, tipe string) {
	base := b.LastInterest - b.Titipan
	if realization.Day() > today.Day() {
		base += b.Interest
	}
	switch {
	case base < 0:
		return base, titipanBunga
	case base > 0:
		return base, tunggakanBunga
	default:
		return base, bungaLabel
	}
}

func parseDate(s string) (time.Time, error) {
	t, err := time.ParseInLocation("2006-01-02", strings.TrimSpace(s), jakartaTZ)
	if err != nil {
		return time.Time{}, errors.New("format tanggal harus YYYY-MM-DD: " + s)
	}
	return t, nil
}

func sameMonthYear(a, b time.Time) bool {
	return a.Year() == b.Year() && a.Month() == b.Month()
}

func monthsBetween(start, end time.Time) int {
	years := end.Year() - start.Year()
	months := int(end.Month()) - int(start.Month())
	return years*12 + months
}

// formatNumberID format int64 dengan separator titik (Indonesia):
// 1500000 → "1.500.000". Negatif mengembalikan "-..." apa adanya.
// Helper di file ini biar package format.go bisa dipakai sender lain.
func formatNumberID(n int64) string {
	if n == 0 {
		return "0"
	}
	negative := n < 0
	if negative {
		n = -n
	}
	s := strconv.FormatInt(n, 10)
	var b strings.Builder
	length := len(s)
	for i, c := range s {
		if i > 0 && (length-i)%3 == 0 {
			b.WriteByte('.')
		}
		b.WriteRune(c)
	}
	if negative {
		return "-" + b.String()
	}
	return b.String()
}
