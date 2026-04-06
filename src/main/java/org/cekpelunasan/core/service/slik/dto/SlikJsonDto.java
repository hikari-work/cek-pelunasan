package org.cekpelunasan.core.service.slik.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representasi data JSON hasil laporan SLIK (Sistem Layanan Informasi Keuangan).
 * Setiap file SLIK yang diunduh dari OJK berbentuk JSON, dan class ini digunakan
 * untuk memetakan JSON tersebut menjadi objek Java yang mudah diakses.
 *
 * <p>Struktur DTO ini mencerminkan hierarki JSON SLIK:
 * laporan → header + individual → data debitur + ringkasan + fasilitas kredit.</p>
 *
 * <p>Field yang tidak dikenal dalam JSON diabaikan secara otomatis
 * menggunakan {@link JsonIgnoreProperties} agar tidak error jika OJK
 * menambahkan field baru di masa depan.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlikJsonDto(
    Header header,
    Individual individual
) {

    /**
     * Informasi header laporan SLIK, berisi kode referensi permintaan
     * dan tanggal kapan laporan tersebut diminta dari sistem OJK.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
        /** Kode referensi unik yang diberikan OJK untuk setiap permintaan. */
        String kodeReferensiPengguna,
        /** Tanggal dan waktu permintaan dalam format {@code yyyyMMddHHmmss}. */
        String tanggalPermintaan
    ) {}

    /**
     * Data inti debitur individu yang dicari, mencakup informasi pribadi,
     * ringkasan kualitas kredit, dan daftar seluruh fasilitas kreditnya.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Individual(
        /** Daftar data pokok debitur; biasanya hanya berisi satu elemen. */
        List<DataPokokDebitur> dataPokokDebitur,
        /** Ringkasan kualitas fasilitas kredit secara keseluruhan. */
        RingkasanFasilitas ringkasanFasilitas,
        /** Daftar lengkap semua fasilitas kredit yang pernah dimiliki debitur. */
        Fasilitas fasilitas
    ) {}

    /**
     * Data identitas dan profil dasar debitur: nama, nomor KTP, alamat,
     * tanggal lahir, dan informasi pekerjaan.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPokokDebitur(
        String namaDebitur,
        String noIdentitas,
        String alamat,
        String jenisKelaminKet,
        String tempatLahir,
        String tanggalLahir,
        String pekerjaanKet,
        String kabKotaKet
    ) {}

    /**
     * Ringkasan kualitas fasilitas kredit debitur secara agregat:
     * kolektibilitas terburuk yang pernah tercatat, total plafon, dan
     * total baki debet dari semua fasilitas.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RingkasanFasilitas(
        /** Nilai kolektibilitas terburuk yang pernah tercatat (1–5). */
        String kualitasTerburuk,
        /** Periode bulan-tahun saat kolektibilitas terburuk terjadi, format {@code yyyyMM}. */
        String kualitasBulanDataTerburuk,
        /** Total plafon dari semua fasilitas aktif (dalam rupiah). */
        String plafonEfektifTotal,
        /** Total sisa hutang dari semua fasilitas aktif (dalam rupiah). */
        String bakiDebetTotal
    ) {}

    /**
     * Pembungkus daftar fasilitas kredit (pinjaman atau pembiayaan)
     * yang dimiliki atau pernah dimiliki oleh debitur.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fasilitas(
        /** Daftar fasilitas kredit/pembiayaan milik debitur. */
        List<KreditPembiayaan> kreditPembiayan
    ) {}

    /**
     * Detail satu fasilitas kredit atau pembiayaan. Class ini menggunakan
     * {@code class} biasa (bukan {@code record}) karena {@link JsonAnySetter}
     * tidak didukung pada Java record — dibutuhkan untuk menangkap field
     * dinamis seperti {@code tahunBulan01}, {@code tahunBulan01Kol}, dan
     * {@code tahunBulan01Ht} yang jumlahnya bisa sampai 24 bulan.
     */
    @Getter
    public static class KreditPembiayaan {
        /** Nama Lembaga Jasa Keuangan (bank/koperasi) yang memberikan kredit. */
        private String ljkKet;
        /** Nama cabang LJK tempat kredit diberikan. */
        private String cabangKet;
        /** Plafon awal kredit yang disetujui (dalam rupiah). */
        private String plafonAwal;
        /** Sisa hutang kredit saat ini (dalam rupiah). */
        private String bakiDebet;
        /** Kondisi fasilitas kredit (aktif, lunas, dll). */
        private String kondisiKet;
        /** Kualitas kolektibilitas kredit saat ini. */
        private String kualitasKet;
        /** Tanggal akad kredit ditandatangani, format {@code yyyyMMdd}. */
        private String tanggalAkadAwal;
        /** Tanggal jatuh tempo kredit, format {@code yyyyMMdd}. */
        private String tanggalJatuhTempo;
        /** Jenis kredit (KUR, KMK, KPR, dll). */
        private String jenisKreditPembiayaanKet;
        /** Tujuan penggunaan kredit. */
        private String jenisPenggunaanKet;
        /** Sifat kredit (baru, perpanjangan, dll). */
        private String sifatKreditPembiayaanKet;

        /**
         * Menampung semua field historis per bulan (24 bulan terakhir).
         * Setiap periode memiliki tiga field:
         * <ul>
         *   <li>{@code tahunBulanNN} — periode dalam format {@code yyyyMM}</li>
         *   <li>{@code tahunBulanNNKol} — kolektibilitas pada periode tersebut</li>
         *   <li>{@code tahunBulanNNHt} — hari tunggakan pada periode tersebut</li>
         * </ul>
         */
        private final Map<String, String> tahunBulan = new LinkedHashMap<>();

        /**
         * Menangkap field dinamis yang namanya diawali dengan "tahunBulan"
         * dan menyimpannya ke map {@link #tahunBulan}. Field lain yang tidak
         * dikenal langsung diabaikan.
         *
         * @param key   nama field JSON yang tidak dikenal
         * @param value nilai field tersebut
         */
        @JsonAnySetter
        public void handleUnknown(String key, Object value) {
            if (key.startsWith("tahunBulan")) {
                tahunBulan.put(key, value != null ? value.toString() : "");
            }
            // field lain yang tidak dikenal → diabaikan
        }
    }
}
