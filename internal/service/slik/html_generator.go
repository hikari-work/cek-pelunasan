package slik

import (
	"fmt"
	"html"
	"strconv"
	"strings"
	"time"
)

// HTMLGenerator generates iDeb HTML report from SLIK JSON data.
type HTMLGenerator struct {
	LogoURL string // URL for logo image (default: "logo.png")
}

// GenerateHTML creates HTML report from JsonDto.
// fasilitasAktif=true filters to show only active facilities.
func (g *HTMLGenerator) GenerateHTML(dto *JsonDto, fasilitasAktif bool) string {
	if dto == nil {
		return ""
	}

	var b strings.Builder
	g.writeDocumentHead(&b, dto)
	g.writeStyles(&b)
	g.writeHeader(&b)
	g.writeDebiturInfo(&b, dto)
	g.writeKreditTable(&b, dto, fasilitasAktif)
	g.writeRiwayatTable(&b, dto, fasilitasAktif)
	g.writeFooter(&b, dto)
	g.writeSignatureTable(&b)
	g.writePrintButtons(&b)
	g.writeDocumentClose(&b)
	g.writeScripts(&b)

	return b.String()
}

func (g *HTMLGenerator) writeDocumentHead(b *strings.Builder, dto *JsonDto) {
	title := dto.Header.KodeReferensiPengguna
	if title == "" {
		title = "Resume iDeb"
	}
	b.WriteString("\n<!DOCTYPE html>\n")
	b.WriteString("<html lang=\"en\">\n")
	b.WriteString("<head>\n")
	b.WriteString("    <meta charset=\"UTF-8\">\n")
	b.WriteString("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
	fmt.Fprintf(b, "    <title>%s</title>\n", html.EscapeString(title))
	b.WriteString("\n</head>\n")
	b.WriteString("<body>\n")
	b.WriteString("    <div class=\"printableArea\">\n")
}

func (g *HTMLGenerator) writeStyles(b *strings.Builder) {
	b.WriteString(`        <style>
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
`)
}

func (g *HTMLGenerator) writeHeader(b *strings.Builder) {
	logoURL := g.LogoURL
	if logoURL == "" {
		logoURL = "logo.png"
	}

	b.WriteString(`        <table class="slik-header" style="width: 100%; border: none; margin-bottom: 20px; border-collapse: collapse;">
            <tr>
                <td style="border: none; vertical-align: middle; text-align: left;">
                    <h3 style="margin: 2px 0; font-family: sans-serif;">Resume Informasi Debitur (iDeb)</h3>
                    <h3 style="margin: 2px 0; font-family: sans-serif;">Perorangan</h3>
                </td>
                <td style="border: none; vertical-align: middle; text-align: right; width: 1%; white-space: nowrap;">
                    <img src="` + html.EscapeString(logoURL) + `" alt="Logo BSY" width="160">
                </td>
            </tr>
        </table>
`)
}

func (g *HTMLGenerator) writeDebiturInfo(b *strings.Builder, dto *JsonDto) {
	var noIdentitas, namaDebitur, alamat string
	if len(dto.Individual.DataPokokDebitur) > 0 {
		deb := dto.Individual.DataPokokDebitur[0]
		noIdentitas = deb.NoIdentitas
		namaDebitur = deb.NamaDebitur
		alamat = deb.Alamat
	}

	b.WriteString("        \n")
	b.WriteString("        <div style=\"font-family: sans-serif;\">\n")
	fmt.Fprintf(b, "          <div><code>ID: <b>%s</b></code></div>\n", html.EscapeString(noIdentitas))
	fmt.Fprintf(b, "          <div><code>Nama: <b>%s</b></code></div>\n", html.EscapeString(namaDebitur))
	fmt.Fprintf(b, "          <div><code>Alamat: <b>%s</b></code></div>\n", html.EscapeString(alamat))
	b.WriteString("        </div>\n\n")
	b.WriteString("\n\n")
}

func (g *HTMLGenerator) writeKreditTable(b *strings.Builder, dto *JsonDto, fasilitasAktif bool) {
	facilities := dto.Individual.Fasilitas
	if facilities == nil {
		facilities = &Fasilitas{}
	}

	var filtered []KreditPembiayaan
	if fasilitasAktif {
		// Filter only active facilities (kondisiKet = "LANCAR" or similar)
		for _, k := range facilities.KreditPembiayan {
			if isActiveFacility(k.KondisiKet) {
				filtered = append(filtered, k)
			}
		}
	} else {
		filtered = facilities.KreditPembiayan
	}

	// Count bank types
	umum, bpr, lembaga, lainnya := CountBankTypes(filtered)

	b.WriteString("        <small><b>Kredit / Pembiayaan:</b></small><br>\n")
	fmt.Fprintf(b, "        <small><i>Bank Umum (%d), BPR/S (%d), Lembaga Pembiayaan (%d), Lainnya (%d)</i></small>\n", umum, bpr, lembaga, lainnya)
	b.WriteString("        <table style=\"font-size: 70%;\">\n")
	b.WriteString("            <thead>\n")
	b.WriteString("                <tr>\n")
	b.WriteString("                    <th style=\"text-align: center;\">#</th>\n")
	b.WriteString("                    <th>Lembaga Jasa Keuangan</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Realisasi</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Jth. Tempo</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\" colspan=\"2\">Suku Bunga</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Plafon Awal</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Baki Debet</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Tgk. Bunga</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Tgk. Pokok</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Kualitas</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Jns. Penggunaan</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Sektor Ekonomi</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Kondisi</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\">Restruk.</th>\n")
	b.WriteString("                </tr>\n")
	b.WriteString("            </thead>\n")
	b.WriteString("            <tbody>\n")

	// Write facility rows
	var totalPlafon, totalBakiDebet, totalTgkBunga, totalTgkPokok int64
	for i, k := range filtered {
		rowClass := ""
		if ClassifyBank(k.LjkKet) == BankTypeBPR {
			rowClass = "bpr"
		}
		fmt.Fprintf(b, "                                <tr class=\"%s\">\n", rowClass)
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%d</td>\n", i+1)
		fmt.Fprintf(b, "                    <td> - %s</td>\n", html.EscapeString(k.LjkKet))
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", formatDateSlash(k.TanggalAkadAwal))
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", formatDateSlash(k.TanggalJatuhTempo))
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatSukuBunga(k.SukuBunga))
		fmt.Fprintf(b, "                    <td style=\"text-align: left;\">%s</td>\n", html.EscapeString(k.SukuBungaKet))
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatNumberDots(k.PlafonAwal))
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatNumberDots(k.BakiDebet))
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatNumberDots(k.TunggakanBunga))
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatNumberDots(k.TunggakanPokok))
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.KualitasKet))
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.JenisPenggunaanKet))
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.SektorEkonomiKet))
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.KondisiKet))
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">\n")
		fmt.Fprintf(b, "                        %s                    </td>\n", orDefault(k.RestrukturisasiKet, "Tidak"))
		b.WriteString("                </tr>\n")

		// Accumulate totals
		totalPlafon += parseNumber(k.PlafonAwal)
		totalBakiDebet += parseNumber(k.BakiDebet)
		totalTgkBunga += parseNumber(k.TunggakanBunga)
		totalTgkPokok += parseNumber(k.TunggakanPokok)
	}

	b.WriteString("                                <!-- Anda dapat menambahkan lebih banyak baris di sini -->\n")
	b.WriteString("                <tr>\n")
	b.WriteString("                    <td colspan=\"6\"><b>Total</b></td>\n")
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%s</b></td>\n", formatNumberDots(strconv.FormatInt(totalPlafon, 10)))
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%s</b></td>\n", formatNumberDots(strconv.FormatInt(totalBakiDebet, 10)))
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%s</b></td>\n", formatNumberDots(strconv.FormatInt(totalTgkBunga, 10)))
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%s</b></td>\n", formatNumberDots(strconv.FormatInt(totalTgkPokok, 10)))
	b.WriteString("                    <td colspan=\"3\"></td>\n")
	b.WriteString("                    <td style=\"text-align: right;\" contenteditable=\"true\"></td>\n")
	b.WriteString("                    <td style=\"text-align: right;\"></td>\n")
	b.WriteString("                </tr>\n")
	b.WriteString("            </tbody>\n")
	b.WriteString("        </table>\n")

	// Calculate worst quality per type
	wUmum, wBpr, wLain := GetWorstQualityPerType(filtered)
	fmt.Fprintf(b, "        <small><i>Kualitas Terburuk/Terendah:</i> Umum (%s), BPR/S* (%s), Lainnya (%s)</small><br>\n",
		html.EscapeString(wUmum), html.EscapeString(wBpr), html.EscapeString(wLain))
	b.WriteString("        <br>\n")
}

func (g *HTMLGenerator) writeRiwayatTable(b *strings.Builder, dto *JsonDto, fasilitasAktif bool) {
	facilities := dto.Individual.Fasilitas
	if facilities == nil {
		return
	}

	var filtered []KreditPembiayaan
	if fasilitasAktif {
		for _, k := range facilities.KreditPembiayan {
			if isActiveFacility(k.KondisiKet) {
				filtered = append(filtered, k)
			}
		}
	} else {
		filtered = facilities.KreditPembiayan
	}

	// Get worst quality from ringkasan or calculate from filtered facilities
	worstQuality := "0"
	if dto.Individual.RingkasanFasilitas != nil && dto.Individual.RingkasanFasilitas.KualitasTerburuk != "" {
		worstQuality = dto.Individual.RingkasanFasilitas.KualitasTerburuk
	} else {
		maxQ := 0
		for _, k := range filtered {
			q, _ := strconv.Atoi(k.Kualitas)
			if q > maxQ {
				maxQ = q
			}
		}
		if maxQ > 0 {
			worstQuality = strconv.Itoa(maxQ)
		}
	}

	b.WriteString("        <small>Riwayat Kualitas:</small><br>\n")
	fmt.Fprintf(b, "        <small><i>Kualitas Terburuk/Terendah (%s)</i></small>\n", html.EscapeString(worstQuality))
	b.WriteString("        <table style=\"font-size: 60%;\">\n")

	// Build month headers (up to 24 months)
	months := g.extractMonthHeaders(filtered)
	g.writeRiwayatHeaders(b, months)

	b.WriteString("            <tbody>\n")
	for i, k := range filtered {
		g.writeRiwayatRow(b, i+1, &k, months)
	}
	b.WriteString("                                <!-- Anda dapat menambahkan lebih banyak baris di sini -->\n")
	b.WriteString("            </tbody>    \n")
	b.WriteString("        </table>\n\n")
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

func (g *HTMLGenerator) writeRiwayatHeaders(b *strings.Builder, months []string) {
	b.WriteString("                        <thead>\n")
	b.WriteString("                <tr>\n")
	b.WriteString("                    <th style=\"text-align: center;\" rowspan=\"2\">#</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\" rowspan=\"2\">Bulan Data</th>\n")
	b.WriteString("                    <th style=\"text-align: center;\" colspan=\"48\">Kualitas / Hari Tunggakan</th>\n")
	b.WriteString("                </tr>\n")
	b.WriteString("                <tr>\n")

	for _, m := range months {
		label := formatMonthLabel(m)
		fmt.Fprintf(b, "                    <th style=\"text-align: center;\" colspan=\"2\">%s</th>\n", html.EscapeString(label))
	}
	b.WriteString("                </tr>\n")
	b.WriteString("            </thead>\n")
}

func (g *HTMLGenerator) writeRiwayatRow(b *strings.Builder, index int, k *KreditPembiayaan, months []string) {
	b.WriteString("                                <tr>\n")
	fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%d</td>\n", index)

	// Get latest month from tahunBulan data
	latestMonth := GetLatestMonth(k.TahunBulan)
	fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", html.EscapeString(latestMonth))

	// Write kol and ht for each month
	for i := 1; i <= 24; i++ {
		kolKey := fmt.Sprintf("tahunBulan%02dKol", i)
		htKey := fmt.Sprintf("tahunBulan%02dHt", i)
		kol := k.TahunBulan[kolKey]
		ht := k.TahunBulan[htKey]

		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", html.EscapeString(kol))
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", html.EscapeString(ht))
	}
	b.WriteString("                </tr>\n")
}

func (g *HTMLGenerator) writeSignatureTable(b *strings.Builder) {
	b.WriteString("        <div style=\"display: flex; justify-content: flex-end; margin-top: 20px; text-align: right;\">\n")
	b.WriteString("          <div style=\"display: inline-block;\">\n")
	b.WriteString("            <table style=\"width: 300px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; page-break-inside: avoid;\">\n")
	b.WriteString("              <tbody>\n")
	b.WriteString("                <tr>\n")
	b.WriteString("                  <td style=\"border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;\">Petugas</td>\n")
	b.WriteString("                  <td style=\"border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;\">Pemeriksa</td>\n")
	b.WriteString("                  <td style=\"border: 0.5px solid blue; font-weight: bold; color: blue; text-align: center; padding: 4px; width: 100px;\">Pimpinan</td>\n")
	b.WriteString("                </tr>\n")
	b.WriteString("                <tr>\n")
	b.WriteString("                  <td style=\"border: 0.5px solid blue; height: 60px;\"></td>\n")
	b.WriteString("                  <td style=\"border: 0.5px solid blue; height: 60px;\"></td>\n")
	b.WriteString("                  <td style=\"border: 0.5px solid blue; height: 60px;\"></td>\n")
	b.WriteString("                </tr>\n")
	b.WriteString("              </tbody>\n")
	b.WriteString("            </table>\n")
	b.WriteString("          </div>\n")
	b.WriteString("        </div>\n")
}

func (g *HTMLGenerator) writeFooter(b *strings.Builder, dto *JsonDto) {
	b.WriteString("        \n")
	tanggal := formatDateTimeSlash(dto.Header.TanggalPermintaan)
	kodeRef := dto.Header.KodeReferensiPengguna
	tujuan := mapTujuanCode(dto.Header.TujuanPenggunaan)
	petugas := orDefault(dto.Header.PetugasPermintaan, "Petugas Bank")
	fmt.Fprintf(b, "        <small><i>Tanggal Permintaan <b>%s</b>, Kode Ref. Pengguna <b>%s</b>, Tujuan Penggunaan <b>%s</b>, Petugas Permintaan <b>%s</b></i></small><br>\n",
		html.EscapeString(tanggal), html.EscapeString(kodeRef), html.EscapeString(tujuan), html.EscapeString(petugas))
	b.WriteString("        <small> - <i>PT Bank Perekonomian Rakyat Surya Yudhakencana</i></small><br>\n\n")
	b.WriteString("\n\n")
}

func (g *HTMLGenerator) writePrintButtons(b *strings.Builder) {
	b.WriteString("    </div>\n")
}

func (g *HTMLGenerator) writeDocumentClose(b *strings.Builder) {
	b.WriteString("</body>\n")
	b.WriteString("</html>\n")
}

func (g *HTMLGenerator) writeScripts(b *strings.Builder) {
	b.WriteString(`<script src="js/jquery-3.2.1.min.js"></script>
<script src="js/jquery.PrintArea.js" type="text/JavaScript"></script>
<script>
    $(document).ready(function() {
        $("#print").click(function() {
            var mode = 'iframe'; //popup
            var close = mode == "popup";
            var options = {
                mode: mode,
                popClose: close
            };
            $("div.printableArea").printArea(options);
        });

        // function to add row
        $('.add-row').click(function () {
            var $row = $('#mytable').find('tr.hidden').clone(true).removeClass('hidden');
            $('#mytable').find('table').append($row);
        });

        // function to remove row
        $('.delete-row').click(function () {
            $(this).parents('tr').detach();
        });

    });
</script>
`)
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
