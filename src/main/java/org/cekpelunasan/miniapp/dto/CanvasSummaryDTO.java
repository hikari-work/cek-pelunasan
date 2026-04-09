package org.cekpelunasan.miniapp.dto;

import java.math.BigDecimal;

/**
 * Ringkasan data nasabah tabungan untuk fitur canvasing di Mini App.
 * Nasabah yang muncul di sini adalah yang belum memiliki tagihan aktif.
 */
public record CanvasSummaryDTO(
        String tabId,
        String name,
        String branch,
        String type,
        BigDecimal balance,
        String cif,
        String address
) {}
