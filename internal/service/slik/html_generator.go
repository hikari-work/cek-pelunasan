package slik

import (
	"fmt"
	"html/template"
	"strconv"
	"strings"
	"time"
)

// HTMLGenerator generates iDeb HTML report from SLIK JSON data.
type HTMLGenerator struct {
	LogoURL string // URL for logo image (default: "logo.png")
}

// GenerateHTML creates HTML report from JsonDto using html/template.
func (g *HTMLGenerator) GenerateHTML(dto *JsonDto, fasilitasAktif bool) string {
	if dto == nil {
		return ""
	}

	// Prepare data for template
	data := g.prepareTemplateData(dto, fasilitasAktif)

	var b strings.Builder
	if err := slikTemplate.Execute(&b, data); err != nil {
		return fmt.Sprintf("Error generating HTML: %v", err)
	}

	return b.String()
}

type templateData struct {
	DTO          *JsonDto
	Debitur      DataPokokDebitur
	Filtered     []KreditPembiayaan
	Counts       counts
	Worst        worst
	WorstOverall string
	Totals       totals
	Months       []string
	LogoURL      string
}

type counts struct {
	Umum, BPR, Lembaga, Lainnya int
}

type worst struct {
	Umum, BPR, Lainnya string
}

type totals struct {
	Plafon, BakiDebet, TgkBunga, TgkPokok int64
}

func (g *HTMLGenerator) prepareTemplateData(dto *JsonDto, fasilitasAktif bool) templateData {
	logoURL := g.LogoURL
	if logoURL == "" {
		logoURL = "logo.png"
	}

	var debitur DataPokokDebitur
	if len(dto.Individual.DataPokokDebitur) > 0 {
		debitur = dto.Individual.DataPokokDebitur[0]
	}

	var filtered []KreditPembiayaan
	if fasilitasAktif {
		if dto.Individual.Fasilitas != nil {
			for _, k := range dto.Individual.Fasilitas.KreditPembiayan {
				if isActiveFacility(k.KondisiKet) {
					filtered = append(filtered, k)
				}
			}
		}
	} else {
		if dto.Individual.Fasilitas != nil {
			filtered = dto.Individual.Fasilitas.KreditPembiayan
		}
	}

	cUmum, cBpr, cLembaga, cLainnya := CountBankTypes(filtered)
	wUmum, wBpr, wLainnya := GetWorstQualityPerType(filtered)

	var t totals
	for _, k := range filtered {
		t.Plafon += parseNumber(k.PlafonAwal)
		t.BakiDebet += parseNumber(k.BakiDebet)
		t.TgkBunga += parseNumber(k.TunggakanBunga)
		t.TgkPokok += parseNumber(k.TunggakanPokok)
	}

	worstOverall := "0"
	if dto.Individual.RingkasanFasilitas != nil && dto.Individual.RingkasanFasilitas.KualitasTerburuk != "" {
		worstOverall = dto.Individual.RingkasanFasilitas.KualitasTerburuk
	} else {
		maxQ := 0
		for _, k := range filtered {
			q, _ := strconv.Atoi(k.Kualitas)
			if q > maxQ {
				maxQ = q
			}
		}
		if maxQ > 0 {
			worstOverall = strconv.Itoa(maxQ)
		}
	}

	return templateData{
		DTO:          dto,
		Debitur:      debitur,
		Filtered:     filtered,
		Counts:       counts{cUmum, cBpr, cLembaga, cLainnya},
		Worst:        worst{wUmum, wBpr, wLainnya},
		WorstOverall: worstOverall,
		Totals:       t,
		Months:       g.extractMonthHeaders(filtered),
		LogoURL:      logoURL,
	}
}

func (g *HTMLGenerator) extractMonthHeaders(facilities []KreditPembiayaan) []string {
	// Extract unique months from tahunBulan fields
	monthSet := make(map[string]bool)
	for _, k := range facilities {
		for key, val := range k.TahunBulan {
			if strings.HasPrefix(key, "tahunBulan") && !strings.HasSuffix(key, "Kol") && !strings.HasSuffix(key, "Ht") {
				if val != "" && len(val) >= 6 {
					monthSet[val[:6]] = true
				}
			}
		}
	}

	// Convert to sorted list (newest first)
	var months []string
	for m := range monthSet {
		months = append(months, m)
	}
	// Sort descending (newest first)
	for i := 0; i < len(months)-1; i++ {
		for j := i + 1; j < len(months); j++ {
			if months[i] < months[j] {
				months[i], months[j] = months[j], months[i]
			}
		}
	}

	// Pad to 24 months with empty strings
	for len(months) < 24 {
		months = append(months, "")
	}
	return months[:24]
}

// Helper functions

func isActiveFacility(kondisi string) bool {
	// Consider facility active if NOT closed/paid off
	// Active = any status except LUNAS (paid off), HAPUS BUKU (written off), TUTUP (closed)
	kondisi = strings.ToUpper(strings.TrimSpace(kondisi))

	// Exclude closed/inactive statuses
	inactiveStatuses := []string{
		"LUNAS",           // Paid off
		"HAPUS BUKU",      // Written off
		"HAPUS",           // Deleted
		"TUTUP",           // Closed
		"DITUTUP",         // Closed
		"CLOSED",          // Closed (English)
		"PAID OFF",        // Paid off (English)
	}

	for _, inactive := range inactiveStatuses {
		if kondisi == inactive {
			return false
		}
	}

	// All other statuses are considered active (LANCAR, DALAM PERHATIAN KHUSUS,
	// KURANG LANCAR, DIRAGUKAN, MACET, etc.)
	return kondisi != ""
}

func formatDateSlash(dateStr string) string {
	if len(dateStr) < 8 {
		return "//"
	}
	t, err := time.Parse("20060102", dateStr[:8])
	if err != nil {
		return "//"
	}
	return t.Format("02/01/2006")
}

func formatDateTimeSlash(dateTimeStr string) string {
	if len(dateTimeStr) < 14 {
		return ""
	}
	t, err := time.Parse("20060102150405", dateTimeStr[:14])
	if err != nil {
		return dateTimeStr
	}
	return t.Format("02/01/2006")
}

func formatMonthLabel(monthStr string) string {
	if len(monthStr) < 6 {
		return "-"
	}
	// YYYYMM -> MM-YY
	year := monthStr[2:4]
	month := monthStr[4:6]
	return month + "-" + year
}

func formatNumberDots(numStr string) string {
	numStr = strings.TrimSpace(numStr)
	if numStr == "" {
		return "0"
	}
	n, err := strconv.ParseInt(numStr, 10, 64)
	if err != nil {
		return numStr
	}
	return formatInt64Dots(n)
}

func formatInt64Dots(n int64) string {
	if n == 0 {
		return "0"
	}
	// Format with dots as thousand separators (Indonesian format)
	s := strconv.FormatInt(n, 10)
	if n < 0 {
		s = s[1:] // Remove minus sign temporarily
	}

	// Add dots from right to left
	var result strings.Builder
	for i, c := range reverseString(s) {
		if i > 0 && i%3 == 0 {
			result.WriteRune('.')
		}
		result.WriteRune(c)
	}

	formatted := reverseString(result.String())
	if n < 0 {
		return "-" + formatted
	}
	return formatted
}

func reverseString(s string) string {
	runes := []rune(s)
	for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
		runes[i], runes[j] = runes[j], runes[i]
	}
	return string(runes)
}

func parseNumber(numStr string) int64 {
	numStr = strings.TrimSpace(numStr)
	if numStr == "" {
		return 0
	}
	n, err := strconv.ParseInt(numStr, 10, 64)
	if err != nil {
		return 0
	}
	return n
}

func formatSukuBunga(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		return "0,00"
	}
	// Try to convert to float and format with comma
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return s
	}
	return strings.Replace(fmt.Sprintf("%.2f", f), ".", ",", 1)
}

func orDefault(s, def string) string {
	if strings.TrimSpace(s) == "" {
		return def
	}
	return s
}

func mapTujuanCode(code string) string {
	code = strings.TrimSpace(code)
	if code == "" {
		return "Analisa Kredit"
	}
	// Official SLIK purpose codes mapping
	switch code {
	case "01":
		return "Penilaian calon Debitur"
	case "02":
		return "Penerapan one obligor concept"
	case "03":
		return "Monitoring Debitur existing"
	case "04":
		return "Melayani permintaan Debitur"
	case "05":
		return "Dalam rangka pelaksanaan audit"
	case "06":
		return "Penanganan pengaduan Debitur"
	case "07":
		return "Penilaian karyawan atau calon karyawan"
	case "08":
		return "Penilaian calon rekanan, agen, merchant, maupun vendor Pelapor"
	case "99":
		return "Lain - lain"
	default:
		return "Analisa Kredit (" + code + ")"
	}
}

var slikTemplate = template.Must(template.New("slik").Funcs(template.FuncMap{
	"formatDate":      formatDateSlash,
	"formatDateTime":  formatDateTimeSlash,
	"formatNumber":    formatNumberDots,
	"formatInt":       formatInt64Dots,
	"formatSukuBunga": formatSukuBunga,
	"mapTujuan":       mapTujuanCode,
	"orDefault":       orDefault,
	"inc":             func(i int) int { return i + 1 },
	"isBPR":           func(ljk string) bool { return ClassifyBank(ljk) == BankTypeBPR },
	"getTahunBulan":   func(m map[string]string, key string) string { return m[key] },
	"formatMonth":     formatMonthLabel,
	"latestMonth":     GetLatestMonth,
	"seq": func(start, end int) []int {
		res := make([]int, end-start+1)
		for i := range res {
			res[i] = start + i
		}
		return res
	},
}).Parse(`
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{or .DTO.Header.KodeReferensiPengguna "Resume iDeb"}}</title>
    <style>
        @page { size: A4 landscape; margin: 15mm; }
        /* CSS untuk gaya tabel */
        table {
            width: 100%;
            border-collapse: collapse;
        }
        th, td {
            border: 1px solid #555;
            text-align: left;
            padding: 2px 5px;
        }
        th {
            background-color: #f2f2f2;
        }
        .bpr {
            background-color: #EBF5FB;
        }
        input[type="tel"] {
            width: 95%;
        }
        table:not(.slik-header) { border-collapse: collapse; }
        .slik-header, .slik-header td, .slik-header th { border: none !important; }
    </style>
</head>
<body>
    <div class="printableArea">
        <table class="slik-header" style="width: 100%; border: none; margin-bottom: 20px; border-collapse: collapse;">
            <tr>
                <td style="border: none; vertical-align: middle; text-align: left;">
                    <h3 style="margin: 2px 0; font-family: sans-serif;">Resume Informasi Debitur (iDeb)</h3>
                    <h3 style="margin: 2px 0; font-family: sans-serif;">Perorangan</h3>
                </td>
                <td style="border: none; vertical-align: middle; text-align: right; width: 1%; white-space: nowrap;">
                    <img src="{{.LogoURL}}" alt="Logo BSY" width="160">
                </td>
            </tr>
        </table>

        <div style="font-family: sans-serif;">
            <div><code>ID: <b>{{.Debitur.NoIdentitas}}</b></code></div>
            <div><code>Nama: <b>{{.Debitur.NamaDebitur}}</b></code></div>
            <div><code>Alamat: <b>{{.Debitur.Alamat}}</b></code></div>
        </div>

        <br>
        <small><b>Kredit / Pembiayaan:</b></small><br>
        <small><i>Bank Umum ({{.Counts.Umum}}), BPR/S ({{.Counts.BPR}}), Lembaga Pembiayaan ({{.Counts.Lembaga}}), Lainnya ({{.Counts.Lainnya}})</i></small>
        <table style="font-size: 70%;">
            <thead>
                <tr>
                    <th style="text-align: center;">#</th>
                    <th>Lembaga Jasa Keuangan</th>
                    <th style="text-align: center;">Realisasi</th>
                    <th style="text-align: center;">Jth. Tempo</th>
                    <th style="text-align: center;" colspan="2">Suku Bunga</th>
                    <th style="text-align: center;">Plafon Awal</th>
                    <th style="text-align: center;">Baki Debet</th>
                    <th style="text-align: center;">Tgk. Bunga</th>
                    <th style="text-align: center;">Tgk. Pokok</th>
                    <th style="text-align: center;">Kualitas</th>
                    <th style="text-align: center;">Jns. Penggunaan</th>
                    <th style="text-align: center;">Sektor Ekonomi</th>
                    <th style="text-align: center;">Kondisi</th>
                    <th style="text-align: center;">Restruk.</th>
                </tr>
            </thead>
            <tbody>
                {{range $i, $k := .Filtered}}
                <tr class="{{if isBPR $k.LjkKet}}bpr{{end}}">
                    <td style="text-align: center;">{{inc $i}}</td>
                    <td> - {{$k.LjkKet}}</td>
                    <td style="text-align: center;">{{formatDate $k.TanggalAkadAwal}}</td>
                    <td style="text-align: center;">{{formatDate $k.TanggalJatuhTempo}}</td>
                    <td style="text-align: right;">{{formatSukuBunga $k.SukuBunga}}</td>
                    <td style="text-align: left;">{{$k.SukuBungaKet}}</td>
                    <td style="text-align: right;">{{formatNumber $k.PlafonAwal}}</td>
                    <td style="text-align: right;">{{formatNumber $k.BakiDebet}}</td>
                    <td style="text-align: right;">{{formatNumber $k.TunggakanBunga}}</td>
                    <td style="text-align: right;">{{formatNumber $k.TunggakanPokok}}</td>
                    <td>{{$k.KualitasKet}}</td>
                    <td>{{$k.JenisPenggunaanKet}}</td>
                    <td>{{$k.SektorEkonomiKet}}</td>
                    <td>{{$k.KondisiKet}}</td>
                    <td style="text-align: center;">{{orDefault $k.RestrukturisasiKet "Tidak"}}</td>
                </tr>
                {{end}}
                <tr>
                    <td colspan="6"><b>Total</b></td>
                    <td style="text-align: right;"><b>{{formatInt .Totals.Plafon}}</b></td>
                    <td style="text-align: right;"><b>{{formatInt .Totals.BakiDebet}}</b></td>
                    <td style="text-align: right;"><b>{{formatInt .Totals.TgkBunga}}</b></td>
                    <td style="text-align: right;"><b>{{formatInt .Totals.TgkPokok}}</b></td>
                    <td colspan="3"></td>
                    <td style="text-align: right;" contenteditable="true"></td>
                    <td style="text-align: right;"></td>
                </tr>
            </tbody>
        </table>
        <small><i>Kualitas Terburuk/Terendah:</i> Umum ({{.Worst.Umum}}), BPR/S* ({{.Worst.BPR}}), Lainnya ({{.Worst.Lainnya}})</small><br>
        <br>
        <small>Riwayat Kualitas:</small><br>
        <small><i>Kualitas Terburuk/Terendah ({{.WorstOverall}})</i></small>
        <table style="font-size: 60%;">
            <thead>
                <tr>
                    <th style="text-align: center;" rowspan="2">#</th>
                    <th style="text-align: center;" rowspan="2">Bulan Data</th>
                    <th style="text-align: center;" colspan="48">Kualitas / Hari Tunggakan</th>
                </tr>
                <tr>
                    {{range .Months}}
                    <th style="text-align: center;" colspan="2">{{formatMonth .}}</th>
                    {{end}}
                </tr>
            </thead>
            <tbody>
                {{range $i, $k := .Filtered}}
                <tr>
                    <td style="text-align: center;">{{inc $i}}</td>
                    <td style="text-align: center;">{{latestMonth $k.TahunBulan}}</td>
                    {{range $idx := seq 1 24}}
                    <td style="text-align: center;">{{getTahunBulan $k.TahunBulan (printf "tahunBulan%02dKol" $idx)}}</td>
                    <td style="text-align: center;">{{getTahunBulan $k.TahunBulan (printf "tahunBulan%02dHt" $idx)}}</td>
                    {{end}}
                </tr>
                {{end}}
            </tbody>
        </table>

        <div style="display: flex; justify-content: flex-end; margin-top: 20px; text-align: right;">
          <div style="display: inline-block;">
            <table style="width: 300px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; page-break-inside: avoid;">
              <tbody>
                <tr>
                  <td style="border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;">Petugas</td>
                  <td style="border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;">Pemeriksa</td>
                  <td style="border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;">Pimpinan</td>
                </tr>
                <tr>
                  <td style="border: 0.5px solid blue; height: 60px;"></td>
                  <td style="border: 0.5px solid blue; height: 60px;"></td>
                  <td style="border: 0.5px solid blue; height: 60px;"></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <small><i>Tanggal Permintaan <b>{{formatDateTime .DTO.Header.TanggalPermintaan}}</b>, Kode Ref. Pengguna <b>{{.DTO.Header.KodeReferensiPengguna}}</b>, Tujuan Penggunaan <b>{{mapTujuan .DTO.Header.TujuanPenggunaan}}</b>, Petugas Permintaan <b>{{orDefault .DTO.Header.PetugasPermintaan "Petugas Bank"}}</b></i></small><br>
        <small> - <i>PT Bank Perekonomian Rakyat Surya Yudhakencana</i></small><br>
    </div>
</body>
</html>
`))
