package slik

import (
	"os"
	"strings"
	"testing"
)

func TestHTMLGenerator_GenerateHTML(t *testing.T) {
	// Load sample JSON
	jsonData := []byte(`{
		"header": {
			"kodeReferensiPengguna": "REF-TEST-001",
			"tanggalPermintaan": "20260524143000"
		},
		"individual": {
			"dataPokokDebitur": [{
				"namaDebitur": "BUDI SANTOSO",
				"noIdentitas": "3175042206680015",
				"alamat": "JL. MAWAR NO. 123 RT 001 RW 002",
				"jenisKelaminKet": "LAKI-LAKI",
				"tempatLahir": "JAKARTA",
				"tanggalLahir": "19680622",
				"pekerjaanKet": "PEGAWAI SWASTA",
				"kabKotaKet": "JAKARTA SELATAN"
			}],
			"ringkasanFasilitas": {
				"kualitasTerburuk": "2",
				"kualitasBulanDataTerburuk": "202604",
				"plafonEfektifTotal": "50000000",
				"bakiDebetTotal": "35000000"
			},
			"fasilitas": {
				"kreditPembiayan": [{
					"ljkKet": "PT BANK ABC INDONESIA",
					"cabangKet": "JAKARTA PUSAT",
					"plafonAwal": "50000000",
					"bakiDebet": "35000000",
					"kondisiKet": "LANCAR",
					"kualitasKet": "1",
					"tanggalAkadAwal": "20240101",
					"tanggalJatuhTempo": "20270101",
					"jenisKreditPembiayaanKet": "KREDIT MODAL KERJA",
					"jenisPenggunaanKet": "MODAL KERJA",
					"sifatKreditPembiayaanKet": "KREDIT BARU",
					"tahunBulan01": "202604",
					"tahunBulan01Kol": "1",
					"tahunBulan01Ht": "0",
					"tahunBulan02": "202603",
					"tahunBulan02Kol": "2",
					"tahunBulan02Ht": "15",
					"tahunBulan03": "202602",
					"tahunBulan03Kol": "1",
					"tahunBulan03Ht": "0"
				}]
			}
		}
	}`)

	dto, err := ParseSlikJSON(jsonData)
	if err != nil {
		t.Fatalf("ParseSlikJSON failed: %v", err)
	}

	gen := &HTMLGenerator{LogoURL: "logo.png"}
	html := gen.GenerateHTML(dto, false)

	// Validate HTML structure
	if !strings.Contains(html, "<!DOCTYPE html>") {
		t.Error("Missing DOCTYPE")
	}
	if !strings.Contains(html, "REF-TEST-001") {
		t.Error("Missing reference code in title")
	}
	if !strings.Contains(html, "BUDI SANTOSO") {
		t.Error("Missing debitur name")
	}
	if !strings.Contains(html, "3175042206680015") {
		t.Error("Missing KTP number")
	}
	if !strings.Contains(html, "PT BANK ABC INDONESIA") {
		t.Error("Missing bank name")
	}
	if !strings.Contains(html, "50.000.000") {
		t.Error("Missing formatted plafon")
	}
	if !strings.Contains(html, "35.000.000") {
		t.Error("Missing formatted baki debet")
	}
	if !strings.Contains(html, "01/01/2027") {
		t.Error("Missing formatted jatuh tempo date")
	}
	if !strings.Contains(html, "Resume Informasi Debitur (iDeb)") {
		t.Error("Missing header title")
	}

	// Write to file for manual inspection
	_ = os.WriteFile("test_output_go.html", []byte(html), 0644)
	t.Logf("HTML output written to test_output_go.html (%d bytes)", len(html))
}

func TestHTMLGenerator_GenerateHTML_FasilitasAktif(t *testing.T) {
	jsonData := []byte(`{
		"header": {
			"kodeReferensiPengguna": "REF-TEST-002",
			"tanggalPermintaan": "20260524143000"
		},
		"individual": {
			"dataPokokDebitur": [{
				"namaDebitur": "TEST USER",
				"noIdentitas": "1234567890123456",
				"alamat": "TEST ADDRESS"
			}],
			"fasilitas": {
				"kreditPembiayan": [
					{
						"ljkKet": "BANK AKTIF",
						"plafonAwal": "10000000",
						"bakiDebet": "5000000",
						"kondisiKet": "LANCAR",
						"kualitasKet": "1"
					},
					{
						"ljkKet": "BANK LUNAS",
						"plafonAwal": "20000000",
						"bakiDebet": "0",
						"kondisiKet": "LUNAS",
						"kualitasKet": "1"
					}
				]
			}
		}
	}`)

	dto, err := ParseSlikJSON(jsonData)
	if err != nil {
		t.Fatalf("ParseSlikJSON failed: %v", err)
	}

	gen := &HTMLGenerator{LogoURL: "logo.png"}

	// Test with fasilitasAktif=false (all facilities)
	htmlAll := gen.GenerateHTML(dto, false)
	if !strings.Contains(htmlAll, "BANK AKTIF") {
		t.Error("Missing active bank in all facilities")
	}
	if !strings.Contains(htmlAll, "BANK LUNAS") {
		t.Error("Missing closed bank in all facilities")
	}
	if !strings.Contains(htmlAll, "30.000.000") { // Total plafon
		t.Error("Missing total plafon for all facilities")
	}

	// Test with fasilitasAktif=true (only active)
	htmlActive := gen.GenerateHTML(dto, true)
	if !strings.Contains(htmlActive, "BANK AKTIF") {
		t.Error("Missing active bank in active-only facilities")
	}
	if strings.Contains(htmlActive, "BANK LUNAS") {
		t.Error("Should not include closed bank in active-only facilities")
	}
	if !strings.Contains(htmlActive, "10.000.000") { // Only active plafon
		t.Error("Missing correct total plafon for active facilities")
	}

	t.Logf("All facilities HTML: %d bytes", len(htmlAll))
	t.Logf("Active facilities HTML: %d bytes", len(htmlActive))
}

func TestFormatNumberDots(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"50000000", "50.000.000"},
		{"1000", "1.000"},
		{"100", "100"},
		{"0", "0"},
		{"", "0"},
		{"123456789", "123.456.789"},
	}

	for _, tt := range tests {
		got := formatNumberDots(tt.input)
		if got != tt.want {
			t.Errorf("formatNumberDots(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

func TestFormatDateSlash(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"20270101", "01/01/2027"},
		{"20260524", "24/05/2026"},
		{"", "//"},
		{"invalid", "//"},
		{"2026", "//"},
	}

	for _, tt := range tests {
		got := formatDateSlash(tt.input)
		if got != tt.want {
			t.Errorf("formatDateSlash(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

func TestFormatMonthLabel(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"202604", "04-26"},
		{"202512", "12-25"},
		{"", "-"},
		{"2026", "-"},
	}

	for _, tt := range tests {
		got := formatMonthLabel(tt.input)
		if got != tt.want {
			t.Errorf("formatMonthLabel(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

func TestIsActiveFacility(t *testing.T) {
	tests := []struct {
		kondisi string
		want    bool
	}{
		// Active statuses (not closed/paid off)
		{"LANCAR", true},
		{"lancar", true},
		{"AKTIF", true},
		{"ACTIVE", true},
		{"MACET", true},                      // NPL but still active (not closed)
		{"DALAM PERHATIAN KHUSUS", true},     // Special mention
		{"KURANG LANCAR", true},              // Substandard
		{"DIRAGUKAN", true},                  // Doubtful

		// Inactive statuses (closed/paid off)
		{"LUNAS", false},                     // Paid off
		{"HAPUS BUKU", false},                // Written off
		{"HAPUS", false},                     // Deleted
		{"TUTUP", false},                     // Closed
		{"DITUTUP", false},                   // Closed
		{"CLOSED", false},                    // Closed (English)
		{"PAID OFF", false},                  // Paid off (English)
		{"", false},                          // Empty
	}

	for _, tt := range tests {
		got := isActiveFacility(tt.kondisi)
		if got != tt.want {
			t.Errorf("isActiveFacility(%q) = %v, want %v", tt.kondisi, got, tt.want)
		}
	}
}
