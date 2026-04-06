package org.cekpelunasan.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Menyediakan fungsi bantu untuk mengolah dan memformat objek tanggal.
 * <p>
 * Saat ini fokus pada ekstraksi tanggal (hari) dari objek {@link LocalDateTime},
 * yang berguna misalnya untuk menampilkan informasi jatuh tempo atau tanggal
 * transaksi dalam pesan bot.
 * </p>
 */
@Component
public class DateUtils {

	/**
	 * Mengambil angka hari (tanggal) dari sebuah {@link LocalDateTime} sebagai string dua digit.
	 * <p>
	 * Method ini memformat tanggal ke pola {@code dd-MM-yyyy} lalu mengambil dua karakter
	 * pertama — yaitu bagian hari. Hasilnya selalu dua karakter, misalnya {@code "01"}, {@code "15"},
	 * atau {@code "31"}.
	 * </p>
	 * <p>
	 * Contoh: jika {@code date} adalah {@code 2024-03-05T10:30:00}, method ini mengembalikan
	 * {@code "05"}.
	 * </p>
	 *
	 * @param date tanggal dan waktu yang ingin diambil nilai harinya, tidak boleh {@code null}
	 * @return string dua digit yang merepresentasikan tanggal (hari dalam bulan)
	 */
	public String converterDate(LocalDateTime date) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
		return date.format(formatter).substring(0, 2);
	}
}
