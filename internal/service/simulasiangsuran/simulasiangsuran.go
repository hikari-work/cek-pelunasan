// Package simulasiangsuran menghitung tiga skenario pembayaran minimal nasabah
// dan merekomendasikan yang totalnya terkecil. Logic murni — tidak butuh DB.
package simulasiangsuran

import (
	"strconv"
	"strings"
	"time"

	"github.com/hikari-work/cek-pelunasan/internal/entity"
)

var jakartaTZ = time.FixedZone("WIB", 7*3600)

// TahapPembayaran adalah satu langkah dalam skenario: kapan, berapa, dan
// alokasinya ke pokok/bunga.
type TahapPembayaran struct {
	Tanggal      time.Time
	JumlahBayar  int64
	AlokasiPokok int64
	AlokasiBunga int64
}

type Skenario struct {
	Kode       string
	Nama       string
	TotalBayar int64
	Tahap      []TahapPembayaran
	Keterangan string
}

type Result struct {
	RekomendasiSkenario string
	TotalBayarMinimum   int64
	Skenarios           []Skenario
}

// Hitung mengembalikan tiga skenario + rekomendasi.
//
//   - A: bayar hari ini saat masih performing (dayLate <= 90).
//     Total = lastInterest + minPrincipal.
//   - B: tunggu sampai hari ke-91 (sudah non-performing). Bayar minPrincipal saja.
//   - C: hybrid — bayar minInterest hari ini, lalu minPrincipal saat hari ke-91.
func Hitung(b *entity.Bills) Result {
	currentDayLate := parseDayLate(b.DayLate)
	now := time.Now().In(jakartaTZ)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, jakartaTZ)

	tunggakanBunga := b.LastInterest
	minPokok := b.MinPrincipal
	minBunga := b.MinInterest

	daysUntil91 := int64(91) - int64(currentDayLate)
	addDays := daysUntil91
	if addDays < 0 {
		addDays = 0
	}
	dateAt91 := today.AddDate(0, 0, int(addDays))

	skenarioA := Skenario{
		Kode:       "A",
		Nama:       "Bayar Hari Ini (Full Performing)",
		TotalBayar: tunggakanBunga + minPokok,
		Tahap: []TahapPembayaran{{
			Tanggal:      today,
			JumlahBayar:  tunggakanBunga + minPokok,
			AlokasiPokok: minPokok,
			AlokasiBunga: tunggakanBunga,
		}},
		Keterangan: "Bayar seluruh tunggakan bunga + minimal pokok sekarang.",
	}

	ketB := "Sudah melewati 91 hari — bayar minimal pokok sekarang."
	if daysUntil91 > 0 {
		ketB = "Bayar minimal pokok pada hari ke-91 (" + strconv.FormatInt(daysUntil91, 10) + " hari lagi). Tidak perlu bayar bunga."
	}
	skenarioB := Skenario{
		Kode:       "B",
		Nama:       "Tunggu 91 Hari (Full Non-Performing)",
		TotalBayar: minPokok,
		Tahap: []TahapPembayaran{{
			Tanggal:      dateAt91,
			JumlahBayar:  minPokok,
			AlokasiPokok: minPokok,
			AlokasiBunga: 0,
		}},
		Keterangan: ketB,
	}

	skenarioC := Skenario{
		Kode:       "C",
		Nama:       "Hybrid (Dua Tahap)",
		TotalBayar: minBunga + minPokok,
		Tahap: []TahapPembayaran{
			{Tanggal: today, JumlahBayar: minBunga, AlokasiBunga: minBunga},
			{Tanggal: dateAt91, JumlahBayar: minPokok, AlokasiPokok: minPokok},
		},
		Keterangan: "Bayar minimal bunga hari ini, lalu minimal pokok saat hari ke-91.",
	}

	skenarios := []Skenario{skenarioA, skenarioB, skenarioC}
	rekom := skenarios[0]
	for _, s := range skenarios[1:] {
		if s.TotalBayar < rekom.TotalBayar {
			rekom = s
		}
	}

	return Result{
		RekomendasiSkenario: rekom.Kode,
		TotalBayarMinimum:   rekom.TotalBayar,
		Skenarios:           skenarios,
	}
}

func parseDayLate(s string) int {
	s = strings.TrimSpace(s)
	if s == "" {
		return 0
	}
	n, err := strconv.Atoi(s)
	if err != nil {
		return 0
	}
	return n
}
