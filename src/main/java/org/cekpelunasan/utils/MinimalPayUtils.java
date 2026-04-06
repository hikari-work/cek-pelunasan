package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.stereotype.Component;

/**
 * Memformat data pembayaran minimal angsuran kredit menjadi teks yang mudah dibaca.
 * <p>
 * Menyajikan informasi minimal yang harus dibayar nasabah dalam satu bulan:
 * pokok, bunga, dan totalnya. Berguna untuk tampilan quick info tanpa perlu
 * membuka detail tagihan lengkap.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class MinimalPayUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	/**
	 * Memformat data tagihan menjadi teks ringkas berisi informasi pembayaran minimal.
	 *
	 * @param bill data tagihan kredit nasabah
	 * @return string teks yang siap ditampilkan di bot, berisi SPK, nama, alamat, dan rincian minimal bayar
	 */
	public String minimalPay(Bills bill) {
		return String.format("""
            🔑 *SPK*: `%s`
            👤 *Nama*: *%s*
            🏠 *Alamat*: %s

            💳 *Minimal Pembayaran*
            • Pokok: %s
            • Bunga: %s

            💰 *TOTAL*: %s
            """,
			bill.getNoSpk(),
			bill.getName(),
			bill.getAddress(),
			rupiahFormatUtils.formatRupiah(bill.getMinPrincipal()),
			rupiahFormatUtils.formatRupiah(bill.getMinInterest()),
			rupiahFormatUtils.formatRupiah(bill.getMinPrincipal() + bill.getMinInterest())
		);
	}

}
