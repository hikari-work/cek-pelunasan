package org.cekpelunasan.utils;

import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Mengubah angka Long menjadi format mata uang Rupiah yang sesuai standar Indonesia.
 * <p>
 * Format yang dihasilkan menggunakan titik (.) sebagai pemisah ribuan dan koma (,)
 * sebagai pemisah desimal — sesuai kebiasaan penulisan angka di Indonesia.
 * Contoh: {@code 1500000} menjadi {@code "Rp1.500.000"}.
 * </p>
 */
@Component
public class RupiahFormatUtils {

	/**
	 * Mengonversi angka ke format Rupiah dengan pemisah ribuan.
	 * <p>
	 * Jika {@code amount} null, dikembalikan string {@code "Rp0"} sebagai nilai aman
	 * agar tampilan tidak rusak saat data tidak tersedia.
	 * </p>
	 *
	 * @param amount nominal dalam satuan Rupiah (bukan sen), boleh {@code null}
	 * @return string berformat Rupiah, misalnya {@code "Rp2.500.000"}
	 */
	public String formatRupiah(Long amount) {
		if (amount == null) return "Rp0";
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
		return df.format(amount);
	}

}
