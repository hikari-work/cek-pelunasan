package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Rekam jejak pengecekan SLIK (Sistem Layanan Informasi Keuangan) untuk setiap nasabah.
 * <p>
 * Setiap kali AO melakukan pengecekan kredit nasabah lewat bot, hasilnya dicatat ke sini.
 * Data ini berguna untuk audit — siapa saja yang sudah dicek, kapan, dan bagaimana hasilnya.
 * </p>
 * <p>
 * Koleksi MongoDB yang dipakai adalah {@code credit_history}.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "credit_history")
public class CreditHistory {

	/**
	 * ID unik untuk setiap record riwayat pengecekan ini, di-generate otomatis oleh MongoDB.
	 */
	@Id
	private String id;

	/**
	 * Waktu saat pengecekan dilakukan, disimpan dalam format Unix timestamp (milidetik).
	 * Gunakan {@code new Date(date)} untuk mengonversinya ke format tanggal yang bisa dibaca.
	 */
	private Long date;

	/**
	 * Nomor ID kredit yang dicek — biasanya berupa nomor CIF atau nomor rekening
	 * yang dimasukkan oleh AO.
	 */
	private String creditId;

	/**
	 * Nomor identitas nasabah yang dicek riwayat kreditnya.
	 */
	private String customerId;

	/**
	 * Nama lengkap nasabah sesuai data yang dikembalikan dari hasil pengecekan SLIK.
	 */
	private String name;

	/**
	 * Hasil status pengecekan, misalnya "CLEAR", "BLACKLIST", atau status lain
	 * sesuai ketentuan OJK.
	 */
	private String status;

	/**
	 * Alamat nasabah sesuai data SLIK.
	 */
	private String address;

	/**
	 * Nomor telepon nasabah yang terdaftar di data SLIK.
	 */
	private String phone;

}
