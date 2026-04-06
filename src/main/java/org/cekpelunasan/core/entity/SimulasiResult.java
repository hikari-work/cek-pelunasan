package org.cekpelunasan.core.entity;

import lombok.*;

/**
 * Wadah hasil perhitungan simulasi pelunasan kredit.
 * <p>
 * Ini bukan entity yang disimpan ke database, melainkan objek sementara
 * yang dipakai untuk membawa hasil kalkulasi dari proses simulasi ke lapisan
 * presentasi. Setelah semua baris {@link Simulasi} dihitung dan diakumulasi,
 * hasilnya dikemas dalam objek ini sebelum ditampilkan ke AO lewat bot.
 * </p>
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimulasiResult {

	/**
	 * Total nominal pokok (Principal) yang harus dimasukkan (dibayar) oleh nasabah
	 * berdasarkan hasil simulasi, dalam satuan rupiah.
	 * "masuk" berarti uang yang harus masuk ke rekening untuk melunasi kredit.
	 */
	private long masukP;

	/**
	 * Total nominal bunga (Interest) yang harus dimasukkan (dibayar) oleh nasabah
	 * berdasarkan hasil simulasi, dalam satuan rupiah.
	 */
	private long masukI;

	/**
	 * Tanggal jatuh tempo terpanjang (paling akhir) dari seluruh baris angsuran
	 * yang masuk dalam simulasi ini, disimpan dalam format Unix timestamp (milidetik).
	 * Digunakan untuk menentukan batas akhir periode simulasi.
	 */
	private long maxDate;

}
