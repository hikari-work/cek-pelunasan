package slik

import (
	"bytes"
	"encoding/json"
	"strings"
)

// JsonDto representasi struktur JSON SLIK (file txt isinya JSON OJK).
// Field tidak dikenal otomatis di-skip; tahunBulanXX (24 bulan terakhir) ditangkap
// di TahunBulan pakai unmarshal map manual karena Go tidak punya equivalent
// JsonAnySetter — diparse dua-pass (struct + map).
type JsonDto struct {
	Header     Header     `json:"header"`
	Individual Individual `json:"individual"`
}

type Header struct {
	KodeReferensiPengguna string `json:"kodeReferensiPengguna"`
	TanggalPermintaan     string `json:"tanggalPermintaan"`
	TujuanPenggunaan      string `json:"kodeTujuanPermintaan"`
	PetugasPermintaan     string `json:"dibuatOleh"`
}

type Individual struct {
	DataPokokDebitur   []DataPokokDebitur  `json:"dataPokokDebitur"`
	RingkasanFasilitas *RingkasanFasilitas `json:"ringkasanFasilitas"`
	Fasilitas          *Fasilitas          `json:"fasilitas"`
}

type DataPokokDebitur struct {
	NamaDebitur     string `json:"namaDebitur"`
	NoIdentitas     string `json:"noIdentitas"`
	Alamat          string `json:"alamat"`
	JenisKelaminKet string `json:"jenisKelaminKet"`
	TempatLahir     string `json:"tempatLahir"`
	TanggalLahir    string `json:"tanggalLahir"`
	PekerjaanKet    string `json:"pekerjaanKet"`
	KabKotaKet      string `json:"kabKotaKet"`
}

type RingkasanFasilitas struct {
	KualitasTerburuk          string `json:"kualitasTerburuk"`
	KualitasBulanDataTerburuk string `json:"kualitasBulanDataTerburuk"`
	PlafonEfektifTotal        string `json:"plafonEfektifTotal"`
	BakiDebetTotal            string `json:"bakiDebetTotal"`
}

type Fasilitas struct {
	// JSON OJK menamai field "kreditPembiayan" (tanpa 'a') — ikut.
	KreditPembiayan []KreditPembiayaan `json:"kreditPembiayan"`
}

// KreditPembiayaan + map dinamis tahunBulan via UnmarshalJSON kustom.
type KreditPembiayaan struct {
	LjkKet                   string `json:"ljkKet"`
	CabangKet                string `json:"cabangKet"`
	PlafonAwal               string `json:"plafonAwal"`
	BakiDebet                string `json:"bakiDebet"`
	KondisiKet               string `json:"kondisiKet"`
	Kualitas                 string `json:"kualitas"`
	KualitasKet              string `json:"kualitasKet"`
	TanggalAkadAwal          string `json:"tanggalAkadAwal"`
	TanggalJatuhTempo        string `json:"tanggalJatuhTempo"`
	JenisKreditPembiayaanKet string `json:"jenisKreditPembiayaanKet"`
	JenisPenggunaanKet       string `json:"jenisPenggunaanKet"`
	SifatKreditPembiayaanKet string `json:"sifatKreditPembiayaanKet"`
	SukuBunga                string `json:"sukuBungaImbalan"`
	SukuBungaKet             string `json:"jenisSukuBungaImbalanKet"`
	TunggakanBunga           string `json:"tunggakanBunga"`
	TunggakanPokok           string `json:"tunggakanPokok"`
	SektorEkonomiKet         string `json:"sektorEkonomiKet"`
	RestrukturisasiKet       string `json:"restrukturisasiKet"`

	// TahunBulan menampung semua field dinamis "tahunBulanNN", "tahunBulanNNKol",
	// "tahunBulanNNHt". Diisi dari raw map saat UnmarshalJSON.
	TahunBulan map[string]string `json:"-"`
}

// UnmarshalJSON dua-pass: bind ke struct lewat alias, lalu scan raw map untuk
// kunci yang berawalan "tahunBulan".
func (k *KreditPembiayaan) UnmarshalJSON(data []byte) error {
	type alias KreditPembiayaan
	var a alias
	if err := json.Unmarshal(data, &a); err != nil {
		return err
	}
	*k = KreditPembiayaan(a)

	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	k.TahunBulan = make(map[string]string, 24)
	for key, v := range raw {
		if !strings.HasPrefix(key, "tahunBulan") {
			continue
		}
		switch t := v.(type) {
		case string:
			k.TahunBulan[key] = t
		case float64:
			// JSON number — convert ke string tanpa decimal kalau bilangan bulat.
			if t == float64(int64(t)) {
				k.TahunBulan[key] = jsonIntString(int64(t))
			} else {
				k.TahunBulan[key] = jsonFloatString(t)
			}
		case nil:
			k.TahunBulan[key] = ""
		default:
			b, _ := json.Marshal(t)
			k.TahunBulan[key] = string(b)
		}
	}
	return nil
}

// ParseSlikJSON parse byte array JSON SLIK. Coba UTF-8 dulu; kalau gagal,
// retry dengan Windows-1252 fallback (legacy convention untuk file lama).
func ParseSlikJSON(data []byte) (*JsonDto, error) {
	dto, err := unmarshalSlik(data)
	if err == nil {
		return dto, nil
	}
	// Fallback Windows-1252: di Go cukup map byte 0x80–0xFF ke rune setara.
	return unmarshalSlik(decodeWindows1252(data))
}

func unmarshalSlik(data []byte) (*JsonDto, error) {
	var dto JsonDto
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber()
	// UseNumber dipakai supaya angka besar tidak kehilangan presisi; tapi kita
	// re-decode pakai default karena field semua string.
	dec = json.NewDecoder(bytes.NewReader(data))
	if err := dec.Decode(&dto); err != nil {
		return nil, err
	}
	return &dto, nil
}

// decodeWindows1252 byte 0x00–0x7F = ASCII; 0xA0–0xFF mostly identical to Unicode;
// 0x80–0x9F = special mapping. Untuk approach minimal, kita anggap sama
// dengan Latin-1 (cukup untuk angka/teks ASCII). Cukup untuk kasus retry.
func decodeWindows1252(b []byte) []byte {
	out := make([]byte, 0, len(b)*2)
	for _, c := range b {
		if c < 0x80 {
			out = append(out, c)
			continue
		}
		// Latin-1 fallback: encode rune sebagai UTF-8.
		r := rune(c)
		out = append(out, []byte(string(r))...)
	}
	return out
}

func jsonIntString(n int64) string {
	b, _ := json.Marshal(n)
	return string(b)
}

func jsonFloatString(f float64) string {
	b, _ := json.Marshal(f)
	return string(b)
}
