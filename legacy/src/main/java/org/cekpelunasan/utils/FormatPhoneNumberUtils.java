package org.cekpelunasan.utils;

import org.springframework.stereotype.Component;

/**
 * Mengubah nomor telepon mentah dari database menjadi format yang ramah dibaca manusia,
 * lengkap dengan emoji yang membedakan nomor HP dan telepon rumah.
 * <p>
 * Nomor dari database biasanya tidak konsisten — ada yang dimulai dengan "0", ada yang tidak.
 * Class ini menangani normalisasi itu sekaligus memformat nomor menjadi pola {@code XXXX-XXXX-XXXX}
 * agar lebih mudah dibaca.
 * </p>
 */
@Component
public class FormatPhoneNumberUtils {

	/**
	 * Memformat nomor telepon menjadi bentuk yang mudah dibaca, dengan emoji penanda jenis nomor.
	 * <p>
	 * Aturan yang berlaku:
	 * <ul>
	 *   <li>Jika {@code phone} null atau kosong, dikembalikan teks "📵 Tidak tersedia"</li>
	 *   <li>Jika nomor belum diawali "0", otomatis ditambahkan di depan</li>
	 *   <li>Nomor yang diawali "08" (HP) mendapat emoji 📱, sisanya (telepon rumah) mendapat ☎️</li>
	 *   <li>Nomor diformat menjadi pola {@code XXXX-XXXX-XXXX}</li>
	 * </ul>
	 * </p>
	 *
	 * @param phone nomor telepon mentah, misalnya {@code "81234567890"} atau {@code "021456789"}
	 * @return nomor yang sudah diformat dengan emoji, misalnya {@code "📱 0812-3456-7890"}
	 */
	public String formatPhoneNumber(String phone) {
		if (phone == null || phone.trim().isEmpty()) {
			return "📵 Tidak tersedia";
		}
		String formatted = phone.startsWith("0") ? phone : "0" + phone;
		return String.format("%s %s",
			formatted.startsWith("08") ? "📱" : "☎️",
			formatted.replaceAll("(\\d{4})(\\d{4})(\\d+)", "$1-$2-$3"));

	}
}
