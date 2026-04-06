package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Data rekening tabungan nasabah yang tersimpan di koleksi MongoDB {@code savings}.
 * <p>
 * Informasi di sini mencakup saldo terkini, saldo minimum, saldo yang diblokir,
 * dan data profil pemilik rekening. Data ini diperbarui secara berkala dari sistem
 * core banking dan dipakai oleh bot untuk menampilkan informasi tabungan nasabah
 * ketika diminta oleh AO.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "savings")
public class Savings {

	/**
	 * ID unik dokumen ini di MongoDB, di-generate otomatis.
	 */
	@Id
	private String id;

	/**
	 * Kode cabang tempat rekening tabungan ini dibuka, misalnya "1075".
	 */
	private String branch;

	/**
	 * Jenis produk tabungan, misalnya "SIMPEDES", "BRITAMA", atau produk simpanan lainnya.
	 */
	private String type;

	/**
	 * Nomor CIF (Customer Information File) pemilik rekening ini.
	 * Satu CIF bisa punya lebih dari satu rekening tabungan.
	 */
	private String cif;

	/**
	 * Nomor rekening tabungan unik yang dicetak di buku tabungan nasabah.
	 * Field ini sering dipakai sebagai kunci pencarian di repository.
	 */
	private String tabId;

	/**
	 * Nama lengkap pemilik rekening tabungan.
	 */
	private String name;

	/**
	 * Alamat pemilik rekening sesuai data yang tercatat di core banking.
	 */
	private String address;

	/**
	 * Saldo efektif rekening saat ini dalam rupiah. Ini adalah saldo yang bisa
	 * ditarik oleh nasabah (sudah dikurangi saldo minimum dan blokir).
	 */
	private BigDecimal balance;

	/**
	 * Nominal transaksi terakhir yang tercatat, dalam rupiah. Bisa berupa
	 * setoran (positif) atau penarikan (negatif) tergantung konteks.
	 */
	private BigDecimal transaction;

	/**
	 * Kode Account Officer yang mengelola nasabah pemilik rekening ini.
	 */
	private String accountOfficer;

	/**
	 * Nomor HP pemilik rekening yang terdaftar di data bank.
	 */
	private String phone;

	/**
	 * Saldo minimum yang harus selalu ada di rekening ini (setoran wajib minimum).
	 * Nasabah tidak bisa menarik uang sampai di bawah angka ini, dalam rupiah.
	 */
	private BigDecimal minimumBalance;

	/**
	 * Saldo yang sedang diblokir karena suatu alasan (misalnya jaminan kredit,
	 * atau pemblokiran oleh pejabat berwenang), dalam rupiah.
	 */
	private BigDecimal blockingBalance;

}
