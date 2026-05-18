package org.cekpelunasan.miniapp.dto;

import java.math.BigInteger;

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
        Long fullPayment,
        String ckpnType,
        BigInteger ckpnNominal,
        String rekeningAutobedet
) {}
