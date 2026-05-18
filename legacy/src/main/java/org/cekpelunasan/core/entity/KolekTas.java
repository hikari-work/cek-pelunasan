package org.cekpelunasan.core.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Data nasabah yang masuk dalam daftar Kolek Tas (Koleksi Tas/Kunjungan).
 * <p>
 * Kolek Tas adalah daftar nasabah bermasalah yang perlu dikunjungi atau
 * dihubungi oleh Account Officer untuk penagihan. Data ini diupload secara
 * berkala oleh admin dan menjadi panduan kerja harian AO di lapangan.
 * </p>
 * <p>
 * Koleksi MongoDB yang dipakai adalah {@code kolek_tas}.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "kolek_tas")
public class KolekTas {

	/**
	 * ID unik dokumen ini di MongoDB, di-generate otomatis.
	 */
	@Id
	private String id;

	/**
	 * Nama atau kode kelompok nasabah. AO biasanya mengelola nasabah dalam
	 * beberapa kelompok binaan.
	 */
	private String kelompok;

	/**
	 * Kode atau nama kantor cabang yang menaungi nasabah ini.
	 */
	private String kantor;

	/**
	 * Nomor rekening simpanan atau pinjaman nasabah di sistem core banking.
	 */
	private String rekening;

	/**
	 * Nama lengkap nasabah.
	 */
	private String nama;

	/**
	 * Alamat lengkap nasabah, berguna bagi AO untuk keperluan kunjungan lapangan.
	 */
	private String alamat;

	/**
	 * Nomor HP nasabah yang bisa dihubungi untuk penagihan atau konfirmasi.
	 */
	private String noHp;

	/**
	 * Level kolektibilitas nasabah ini (1 s/d 5). Semakin tinggi angkanya,
	 * semakin prioritas nasabah ini untuk dikunjungi.
	 */
	private String kolek;

	/**
	 * Nominal tunggakan atau angsuran yang bermasalah, dalam format teks
	 * (bisa sudah diformat dengan pemisah ribuan).
	 */
	private String nominal;

	/**
	 * Kode Account Officer yang bertanggung jawab atas nasabah ini.
	 */
	private String accountOfficer;

	/**
	 * Nomor CIF (Customer Information File) nasabah — identitas unik nasabah
	 * di seluruh produk yang ada di bank.
	 */
	private String cif;
}
