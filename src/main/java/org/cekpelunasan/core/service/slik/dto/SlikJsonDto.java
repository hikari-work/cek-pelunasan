package org.cekpelunasan.core.service.slik.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlikJsonDto(
    Header header,
    Individual individual
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
        String kodeReferensiPengguna,
        String tanggalPermintaan
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Individual(
        List<DataPokokDebitur> dataPokokDebitur,
        RingkasanFasilitas ringkasanFasilitas,
        Fasilitas fasilitas
    ) {}

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RingkasanFasilitas(
        String kualitasTerburuk,
        String kualitasBulanDataTerburuk,
        String plafonEfektifTotal,
        String bakiDebetTotal
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fasilitas(
        List<KreditPembiayaan> kreditPembiayan
    ) {}

    /**
     * KreditPembiayaan menggunakan class biasa (bukan record) karena
     * @JsonAnySetter tidak didukung pada record — dibutuhkan untuk
     * menangkap field dinamis tahunBulan01..24 beserta suffix Kol dan Ht.
     */
    @Getter
    public static class KreditPembiayaan {
        private String ljkKet;
        private String cabangKet;
        private String plafonAwal;
        private String bakiDebet;
        private String kondisiKet;
        private String kualitasKet;
        private String tanggalAkadAwal;
        private String tanggalJatuhTempo;
        private String jenisKreditPembiayaanKet;
        private String jenisPenggunaanKet;
        private String sifatKreditPembiayaanKet;

        /**
         * Menampung semua field tahunBulan* (key = nama field, value = nilainya).
         * Format key:
         *   tahunBulan01       → periode YYYYMM
         *   tahunBulan01Kol    → kolektibilitas periode tersebut
         *   tahunBulan01Ht     → hari tunggakan periode tersebut
         */
        private final Map<String, String> tahunBulan = new LinkedHashMap<>();

        @JsonAnySetter
        public void handleUnknown(String key, Object value) {
            if (key.startsWith("tahunBulan")) {
                tahunBulan.put(key, value != null ? value.toString() : "");
            }
            // field lain yang tidak dikenal → diabaikan
        }
    }
}
