package org.cekpelunasan.miniapp.dto;

/**
 * Ringkasan tagihan untuk tampilan daftar hasil pencarian di Mini App.
 */
public record TagihanSummaryDTO(
        String noSpk,
        String name,
        String branch,
        String product,
        String collectStatus,
        String dayLate,
        Long installment,
        Long fullPayment
) {}
