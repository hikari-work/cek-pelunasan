package org.cekpelunasan.miniapp.dto;

/**
 * Ringkasan satu nasabah dalam kelompok KolekTas untuk ditampilkan di Mini App.
 */
public record KolekTasSummaryDTO(
        String id,
        String kelompok,
        String kantor,
        String rekening,
        String nama,
        String alamat,
        String noHp,
        String kolek,
        String nominal,
        String accountOfficer,
        String cif
) {}
