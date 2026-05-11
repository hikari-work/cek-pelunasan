package org.cekpelunasan.core.entity.simulasiangsuran;

import java.math.BigDecimal;
import java.util.List;

public record SkenarioDetail(
    String kode,
    String namaSkenario,
    BigDecimal totalBayar,
    List<TahapPembayaran> tahapPembayaran,
    String keterangan
) {}
