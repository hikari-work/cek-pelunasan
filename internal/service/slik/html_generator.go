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
            /* CSS untuk gaya tabel */
            table {
                width: 100%;
                border-collapse: collapse;
            }
            th, td {
                border: 1px solid #dddddd;
                text-align: left;
                padding: 1px;
            }
            th {
                background-color: #f2f2f2;
            }
            .bpr {
                background-color: #EBF5FB;
            }
            .right-image {
                position: absolute;
                top: 0;
                right: 0;
                max-width: 200px; /* Adjust the size of the image */
                margin: 10px; /* Adjust margin as needed */
            }
            input[type="tel"] {
                width: 95%;
            }
        </style>
`)
}

func (g *HTMLGenerator) writeHeader(b *strings.Builder) {
	logoURL := g.LogoURL
	if logoURL == "" {
		logoURL = "logo.png"
	}
	b.WriteString("        <h3 style=\"margin-bottom: 1px;margin-top: 1px;\">Resume Informasi Debitur (iDeb)</h3>\n")
	fmt.Fprintf(b, "        <img src=\"%s\" alt=\"Logo BSY\" class=\"right-image\" width=\"160\"> \n", html.EscapeString(logoURL))
	b.WriteString("                <h3 style=\"margin-bottom: 8px;margin-top: 1px;\">Perorangan</h3>\n")
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
	b.WriteString("        <div style=\"display: flex; justify-content: space-between; align-items: flex-start;\">\n")
	b.WriteString("          <!-- Kiri: Info -->\n")
	b.WriteString("          <div style=\"font-family: sans-serif;\">\n")
	fmt.Fprintf(b, "            <div><code>ID: <b>%s</b></code></div>\n", html.EscapeString(noIdentitas))
	fmt.Fprintf(b, "            <div><code>Nama: <b>%s</b></code></div>\n", html.EscapeString(namaDebitur))
	fmt.Fprintf(b, "            <div><code>Alamat: <b>%s</b></code></div>\n", html.EscapeString(alamat))
	b.WriteString("          </div>\n\n")

	// Signature grid - use table instead of CSS grid for wkhtmltopdf compatibility
	// Wrap in div for proper flex alignment
	b.WriteString("          <!-- Kanan: Signature Table -->\n")
	b.WriteString("          <div>\n")
	b.WriteString("            <table style=\"width: 300px; border-collapse: collapse; font-family: sans-serif; font-size: 12px; border: 0.5px solid blue; margin-top: 0px; page-break-inside: avoid;\">\n")
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
		b.WriteString("                                <tr class=\"\">\n")
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%d</td>\n", i+1)
		fmt.Fprintf(b, "                    <td> - %s</td>\n", html.EscapeString(k.LjkKet))
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", formatDateSlash(k.TanggalAkadAwal))
		fmt.Fprintf(b, "                    <td style=\"text-align: center;\">%s</td>\n", formatDateSlash(k.TanggalJatuhTempo))
		b.WriteString("                    <td style=\"text-align: right;\">0,00</td>\n") // Suku bunga not in DTO
		b.WriteString("                    <td style=\"text-align: left;\"></td>\n")
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatNumberDots(k.PlafonAwal))
		fmt.Fprintf(b, "                    <td style=\"text-align: right;\">%s</td>\n", formatNumberDots(k.BakiDebet))
		b.WriteString("                    <td style=\"text-align: right;\">0</td>\n") // Tunggakan not in DTO
		b.WriteString("                    <td style=\"text-align: right;\">0</td>\n")
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.KualitasKet))
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.JenisPenggunaanKet))
		b.WriteString("                    <td></td>\n") // Sektor ekonomi not in DTO
		fmt.Fprintf(b, "                    <td>%s</td>\n", html.EscapeString(k.KondisiKet))
		b.WriteString("                    <td style=\"text-align: center;\">\n")
		b.WriteString("                        Tidak                    </td>\n") // Restrukturisasi not in DTO
		b.WriteString("                </tr>\n")

		// Accumulate totals
		totalPlafon += parseNumber(k.PlafonAwal)
		totalBakiDebet += parseNumber(k.BakiDebet)
	}

	b.WriteString("                                <!-- Anda dapat menambahkan lebih banyak baris di sini -->\n")
	b.WriteString("                <tr>\n")
	b.WriteString("                    <td colspan=\"6\"><b>Total</b></td>\n")
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%s</b></td>\n", formatNumberDots(strconv.FormatInt(totalPlafon, 10)))
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%s</b></td>\n", formatNumberDots(strconv.FormatInt(totalBakiDebet, 10)))
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%d</b></td>\n", totalTgkBunga)
	fmt.Fprintf(b, "                    <td style=\"text-align: right;\"><b>%d</b></td>\n", totalTgkPokok)
	b.WriteString("                    <td colspan=\"3\"></td>\n")
	b.WriteString("                    <td style=\"text-align: right;\" contenteditable=\"true\"></td>\n")
	b.WriteString("                </tr>\n")
	b.WriteString("            </tbody>\n")
	b.WriteString("        </table>\n")
	b.WriteString("        <small><i>Kualitas Terburuk/Terendah:</i> Umum (), BPR/S* (), Lainnya ()</small><br>\n")
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

	// Get worst quality from ringkasan
	worstQuality := "0"
	if dto.Individual.RingkasanFasilitas != nil {
		worstQuality = dto.Individual.RingkasanFasilitas.KualitasTerburuk
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

func (g *HTMLGenerator) writeFooter(b *strings.Builder, dto *JsonDto) {
	b.WriteString("        \n")
	tanggal := formatDateTimeSlash(dto.Header.TanggalPermintaan)
	kodeRef := dto.Header.KodeReferensiPengguna
	fmt.Fprintf(b, "        <small><i>Tanggal Permintaan <b>%s</b>, Kode Ref. Pengguna <b>%s</b>, Tujuan Penggunaan <b></b>, Petugas Permintaan <b></b></i></small><br>\n",
		html.EscapeString(tanggal), html.EscapeString(kodeRef))
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
