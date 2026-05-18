package org.cekpelunasan.miniapp.dto;

import java.util.List;

/**
 * Detail Payment untuk Mini App: identitas SPK + daftar transaksi angsuran +
 * snapshot tunggakan dan minimal dari Bills.
 */
public record PaymentDetailDTO(
        String noSpk,
        String name,
        String branch,
        String product,
        List<PaymentRowDTO> rows,
        Long tunggakanPokok,
        Long tunggakanBunga,
        Long minimalPokok,
        Long minimalBunga
) {}
