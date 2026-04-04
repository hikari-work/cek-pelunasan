package org.cekpelunasan.core.service.slik.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

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
        String bakiDebetTotal,
        String krediturBankUmum,
        String krediturBPRS
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fasilitas(
        List<KreditPembiayaan> kreditPembiayan
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KreditPembiayaan(
        String ljkKet,
        String cabangKet,
        String plafonAwal,
        String bakiDebet,
        String kondisiKet,
        String kualitasKet,
        String tanggalAkadAwal,
        String tanggalJatuhTempo,
        String jenisKreditPembiayaanKet,
        String jenisPenggunaanKet,
        String sifatKreditPembiayaanKet
    ) {}
}
