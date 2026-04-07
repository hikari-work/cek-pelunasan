package org.cekpelunasan.miniapp.dto;

/**
 * Detail pelunasan kredit yang menggabungkan data Bills dengan hasil kalkulasi PelunasanService.
 *
 * <p>Rumus total: Baki Debet + Perhitungan Bunga + Penalty + Denda</p>
 */
public record PelunasanDetailDTO(
        // Identitas
        String spk,
        String nama,
        String alamat,
        String product,

        // Tanggal
        String tglRealisasi,
        String tglJatuhTempo,
        String rencanaPelunasan,

        // Pokok
        Long plafond,
        Long bakiDebet,

        // Bunga
        Long perhitunganBunga,
        String typeBunga,

        // Penalty & Denda
        Long penalty,
        Integer multiplierPenalty,
        Long denda,

        // Total
        Long totalPelunasan
) {}
