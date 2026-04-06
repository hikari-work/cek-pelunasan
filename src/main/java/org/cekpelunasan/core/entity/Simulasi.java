package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Data input simulasi pelunasan untuk satu baris jadwal angsuran.
 * <p>
 * Ketika AO ingin mensimulasikan berapa yang harus dibayar nasabah jika
 * melunasi kreditnya pada tanggal tertentu, sistem akan membaca semua record
 * {@code Simulasi} yang cocok dengan nomor SPK, lalu menghitung total
 * tunggakan, denda, dan keterlambatan.
 * </p>
 * <p>
 * Setiap record di koleksi MongoDB {@code simulasi} mewakili satu periode
 * angsuran (satu baris dalam jadwal angsuran kredit).
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "simulasi")
public class Simulasi {

	/**
	 * ID unik dokumen ini, biasanya berupa UUID yang di-generate saat data diimport.
	 */
	@Id
	private String id;

	/**
	 * Nomor SPK kredit yang terkait dengan baris simulasi ini. Satu SPK bisa
	 * punya banyak baris simulasi (satu per periode angsuran).
	 */
	private String spk;

	/**
	 * Tanggal jatuh tempo untuk periode angsuran ini, dalam format teks
	 * (misalnya "2024-01-15").
	 */
	private String tanggal;

	/**
	 * Urutan angsuran ini dalam keseluruhan jadwal kredit, misalnya "1", "2", dst.
	 * Berguna untuk menentukan berapa angsuran yang masih tersisa.
	 */
	private String sequence;

	/**
	 * Jumlah tunggakan (angsuran yang belum dibayar) untuk periode ini, dalam rupiah.
	 */
	private Long tunggakan;

	/**
	 * Denda keterlambatan yang dikenakan untuk periode ini jika angsuran tidak
	 * dibayar tepat waktu, dalam rupiah.
	 */
	private Long denda;

	/**
	 * Jumlah hari keterlambatan yang dihitung untuk periode angsuran ini.
	 * Angka ini dipakai sebagai faktor dalam perhitungan denda.
	 */
	private Long keterlambatan;
}
