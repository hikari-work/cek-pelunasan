package pelunasan

import (
	"strings"
)

// FormatWhatsApp render hasil pelunasan ke pesan WhatsApp.
//
// Output mengikuti format legacy PelunasanDto.toWhatsAppMessageClean:
// blok header, blok perhitungan, blok tanggal — dengan separator
// dan label yang sama. Sanitize karakter markdown WA pada field teks
// (nama, alamat, tanggal-string) supaya tidak merusak formatting.
func (r *Result) FormatWhatsApp() string {
	var b strings.Builder
	b.WriteString("*DETAIL PELUNASAN KREDIT*\n")
	b.WriteString("═══════════════════════\n\n")

	b.WriteString("*👤 NASABAH*\n")
	b.WriteString("Nama    : " + sanitizeWA(orDash(r.Nama)) + "\n")
	b.WriteString("SPK     : " + sanitizeWA(orDash(r.SPK)) + "\n")
	b.WriteString("Alamat  : " + sanitizeWA(orDash(r.Alamat)) + "\n\n")

	b.WriteString("*💰 PERHITUNGAN*\n")
	b.WriteString("Plafond         : `Rp " + formatNumberID(r.Plafond) + "`\n")
	b.WriteString("Baki Debet      : `Rp " + formatNumberID(r.BakiDebet) + "`\n")
	b.WriteString(r.TypeBunga + "    : `Rp " + formatNumberID(r.PerhitunganBunga) + "`\n")
	b.WriteString("Penalty (" + intStr(r.MultiplierPenalty) + "x)   : `Rp " + formatNumberID(r.Penalty) + "`\n")
	b.WriteString("Denda           : `Rp " + formatNumberID(r.Denda) + "`\n")
	b.WriteString("─────────────────────\n")
	b.WriteString("*TOTAL PELUNASAN : Rp " + formatNumberID(r.TotalPelunasan()) + "*\n\n")

	b.WriteString("*📅 TANGGAL PENTING*\n")
	b.WriteString("Realisasi       : " + sanitizeWA(orDash(r.TglRealisasi)) + "\n")
	b.WriteString("Jatuh Tempo     : " + sanitizeWA(orDash(r.TglJatuhTempo)) + "\n")
	b.WriteString("Rencana Lunas   : " + sanitizeWA(orDash(r.RencanaPelunasan)) + "\n")

	return b.String()
}

// sanitizeWA escape karakter markdown WhatsApp dan bersihkan whitespace
// yang bisa merusak layout (newline ditengah field teks).
func sanitizeWA(s string) string {
	r := strings.NewReplacer(
		`*`, `\*`,
		`_`, `\_`,
		`~`, `\~`,
		"`", "\\`",
		"\n", " ",
		"\r", "",
	)
	return strings.TrimSpace(r.Replace(s))
}

func orDash(s string) string {
	if strings.TrimSpace(s) == "" {
		return "-"
	}
	return s
}

func intStr(n int) string {
	// Multiplier penalty selalu kecil (0..6); manual untuk hindari import strconv lagi.
	return formatNumberID(int64(n))
}
