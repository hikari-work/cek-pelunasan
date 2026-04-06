package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Memformat data tabungan nasabah menjadi teks yang siap ditampilkan di bot.
 * <p>
 * Menyediakan dua format: detail satu nasabah ({@link #getSavings}) dan
 * ringkasan satu halaman untuk banyak nasabah sekaligus ({@link #buildMessage}).
 * Saldo efektif dihitung otomatis dengan mempertimbangkan saldo buku, transaksi pending,
 * saldo minimum wajib, dan saldo yang diblokir.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class SavingsUtils {

	private final RupiahFormatUtils rupiahFormatUtils;

	/**
	 * Memformat detail tabungan satu nasabah menjadi teks yang siap dikirim.
	 * <p>
	 * Menghitung saldo efektif secara otomatis:
	 * Saldo Efektif = (Saldo Buku + Transaksi Pending) - Saldo Minimum - Saldo Blokir
	 * </p>
	 *
	 * @param saving data tabungan nasabah
	 * @return string detail tabungan terformat
	 */
	public String getSavings(Savings saving) {
		StringBuilder message = new StringBuilder();
		long bookBalance = saving.getBalance().add(saving.getTransaction()).longValue();
		long minBalance = saving.getMinimumBalance().longValue();
		long blockBalance = saving.getBlockingBalance().longValue();
		long effectiveBalance = bookBalance - minBalance - blockBalance;

		message.append("👤 *").append(saving.getName()).append("*\n")
			.append("No. Rek: `").append(saving.getTabId()).append("`\n")
			.append("Alamat: ").append(saving.getAddress()).append("\n\n")
			.append("💰 Saldo:\n")
			.append("• Buku: ").append(rupiahFormatUtils.formatRupiah(bookBalance)).append("\n")
			.append("• Min: ").append(rupiahFormatUtils.formatRupiah(minBalance)).append("\n")
			.append("• Block: ").append(rupiahFormatUtils.formatRupiah(blockBalance)).append("\n")
			.append("• Efektif: `").append(rupiahFormatUtils.formatRupiah(effectiveBalance)).append("`\n\n");
		return message.toString();
	}
	/**
	 * Membangun pesan halaman daftar tabungan untuk fitur pagination.
	 * <p>
	 * Menampilkan semua nasabah di halaman tersebut, info halaman saat ini vs total halaman,
	 * dan waktu proses dalam milidetik di bagian bawah pesan.
	 * </p>
	 *
	 * @param savings   halaman data tabungan dari database
	 * @param page      nomor halaman saat ini (0-indexed)
	 * @param startTime waktu awal pemrosesan (System.currentTimeMillis()) untuk menghitung durasi
	 * @return string pesan halaman tabungan yang siap dikirim
	 */
	public String buildMessage(Page<Savings> savings, int page, long startTime) {
		StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
			.append("Halaman ").append(page + 1).append(" dari ").append(savings.getTotalPages()).append("\n\n");
		savings.forEach(saving -> message.append(getSavings(saving)));
		message.append("⏱️ Waktu: ").append(System.currentTimeMillis() - startTime).append("ms");
		return message.toString();
	}
}
