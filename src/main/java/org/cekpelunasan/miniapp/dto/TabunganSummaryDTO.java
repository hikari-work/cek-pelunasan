package org.cekpelunasan.miniapp.dto;

import java.math.BigDecimal;

/**
 * Ringkasan rekening tabungan untuk tampilan daftar hasil pencarian di Mini App.
 */
public record TabunganSummaryDTO(
        String tabId,
        String name,
        String branch,
        String type,
        BigDecimal balance
) {}
