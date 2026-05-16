package org.cekpelunasan.miniapp.dto;

/**
 * Satu baris transaksi angsuran dalam tabel detail Mini App Payment.
 *
 * <p>Pokok diisi nilai {@code nominalAngsuran} hanya jika {@code typePosting} = "P",
 * Bunga diisi {@code nominalAngsuran} hanya jika {@code typePosting} = "I".
 * Total adalah {@code nominalAngsuran + denda + penalti}. Jika {@code denda + penalti > 0}
 * frontend akan menandai baris dengan warna merah.</p>
 */
public record PaymentRowDTO(
        int no,
        String tanggal,
        String typePosting,
        long pokok,
        long bunga,
        long denda,
        long penalti,
        long total,
        boolean highlight
) {}
