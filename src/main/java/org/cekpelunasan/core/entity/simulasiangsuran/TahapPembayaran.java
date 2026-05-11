package org.cekpelunasan.core.entity.simulasiangsuran;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TahapPembayaran(
    LocalDate tanggal,
    BigDecimal jumlahBayar,
    BigDecimal alokasiPokok,
    BigDecimal alokasiBunga
) {}
