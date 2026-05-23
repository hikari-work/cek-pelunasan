package slik

import (
	"strings"
	"testing"
)

func TestParseSlikJSON_basic(t *testing.T) {
	in := []byte(`{
		"header": {"kodeReferensiPengguna": "REF-1", "tanggalPermintaan": "20260519143000"},
		"individual": {
			"dataPokokDebitur": [{
				"namaDebitur": "Budi Santoso",
				"noIdentitas": "3201234567890001",
				"alamat": "Jl. Mawar 1",
				"tempatLahir": "Semarang",
				"tanggalLahir": "19800101",
				"pekerjaanKet": "Pegawai"
			}],
			"ringkasanFasilitas": {
				"kualitasTerburuk": "2",
				"kualitasBulanDataTerburuk": "202604",
				"plafonEfektifTotal": "1500000",
				"bakiDebetTotal": "1200000"
			},
			"fasilitas": {
				"kreditPembiayan": [{
					"ljkKet": "Bank ABC",
					"cabangKet": "Semarang",
					"plafonAwal": "1500000",
					"bakiDebet": "1200000",
					"kondisiKet": "Lancar",
					"kualitasKet": "1",
					"tanggalAkadAwal": "20240101",
					"tanggalJatuhTempo": "20270101",
					"jenisPenggunaanKet": "Modal Kerja",
					"tahunBulan01": "202604",
					"tahunBulan01Kol": "1",
					"tahunBulan01Ht": "0",
					"tahunBulan02": "202603",
					"tahunBulan02Kol": "2",
					"tahunBulan02Ht": "10"
				}]
			}
		}
	}`)
	dto, err := ParseSlikJSON(in)
	if err != nil {
		t.Fatalf("ParseSlikJSON: %v", err)
	}
	if dto.Header.KodeReferensiPengguna != "REF-1" {
		t.Errorf("kode ref = %q", dto.Header.KodeReferensiPengguna)
	}
	if got := dto.Individual.DataPokokDebitur[0].NamaDebitur; got != "Budi Santoso" {
		t.Errorf("nama = %q", got)
	}
	k := dto.Individual.Fasilitas.KreditPembiayan[0]
	if k.LjkKet != "Bank ABC" {
		t.Errorf("ljk = %q", k.LjkKet)
	}
	if got := k.TahunBulan["tahunBulan02Kol"]; got != "2" {
		t.Errorf("tb02kol = %q", got)
	}
}

func TestFormatPage_full(t *testing.T) {
	dto := &JsonDto{
		Individual: Individual{
			DataPokokDebitur: []DataPokokDebitur{{
				NamaDebitur: "Budi", NoIdentitas: "3201234567890001", Alamat: "Jl A",
			}},
			Fasilitas: &Fasilitas{KreditPembiayan: []KreditPembiayaan{{
				LjkKet: "Bank ABC", PlafonAwal: "1500000", BakiDebet: "1200000",
				KondisiKet: "Lancar", KualitasKet: "1",
				TahunBulan: map[string]string{
					"tahunBulan01": "202604", "tahunBulan01Kol": "1", "tahunBulan01Ht": "0",
				},
			}}},
		},
	}
	out := FormatPage(PageData{ContentKey: "2026_05/pdf/SMG_budi.pdf", IDNumber: "3201234567890001", DTO: dto}, 0, 1)
	if !strings.Contains(out, "Budi") {
		t.Errorf("expected name in output, got %q", out)
	}
	if !strings.Contains(out, "Rp1.500.000") {
		t.Errorf("expected formatted rupiah, got %q", out)
	}
	if !strings.Contains(out, "Halaman 1 dari 1") {
		t.Errorf("expected page footer, got %q", out)
	}
}

func TestFormatPage_noData(t *testing.T) {
	out := FormatPage(PageData{ContentKey: "2026_05/pdf/SMG_budi.pdf"}, 0, 1)
	if !strings.Contains(out, "Tidak ditemukan") {
		t.Errorf("expected 'Tidak ditemukan' for missing NIK: %q", out)
	}
	if !strings.Contains(out, "/doc SMG_budi.pdf") {
		t.Errorf("expected doc footer command: %q", out)
	}
}

func TestFolderForMonth(t *testing.T) {
	cases := map[string]string{
		"202605": "2026_05",
		"202612": "2026_12",
		"":       "",
		"abc":    "",
	}
	for in, want := range cases {
		if got := FolderForMonth(in); got != want {
			t.Errorf("FolderForMonth(%q)=%q want %q", in, got, want)
		}
	}
}

func TestSubfolderForExt(t *testing.T) {
	if sub, ct := SubfolderForExt("pdf"); sub != "pdf" || ct != "application/pdf" {
		t.Errorf("pdf: %q %q", sub, ct)
	}
	if sub, _ := SubfolderForExt("xlsx"); sub != "" {
		t.Errorf("unsupported ext should return empty, got %q", sub)
	}
}

func TestExtractDisplayName(t *testing.T) {
	cases := map[string]string{
		"2026_05/pdf/SMG_budi.pdf": "budi",
		"":                         "-",
		"plain.pdf":                "plain",
	}
	for in, want := range cases {
		if got := extractDisplayName(in); got != want {
			t.Errorf("extractDisplayName(%q)=%q want %q", in, got, want)
		}
	}
}
