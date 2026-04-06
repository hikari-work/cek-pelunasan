package org.cekpelunasan.core.entity;

/**
 * Daftar peran (role) yang bisa dimiliki oleh pengguna bot ini.
 * <p>
 * Setiap pengguna yang terdaftar di sistem akan memiliki salah satu dari tiga peran ini.
 * Peran menentukan apa saja yang boleh dilakukan oleh pengguna — mulai dari sekadar
 * melihat data, sampai bisa mengelola seluruh sistem.
 * </p>
 *
 * <ul>
 *   <li>{@link #AO} — Account Officer biasa, bisa mengambil data sesuai wilayah kerjanya.</li>
 *   <li>{@link #PIMP} — Pimpinan cabang, bisa melihat data tapi tidak mengubah apapun.</li>
 *   <li>{@link #ADMIN} — Administrator penuh, bisa melakukan semua operasi termasuk yang sensitif.</li>
 * </ul>
 */
public enum AccountOfficerRoles {

	/**
	 * Peran Account Officer (AO). Pengguna dengan peran ini bisa mengambil dan melihat
	 * data tagihan serta nasabah yang ada dalam tanggung jawabnya.
	 */
	AO,

	/**
	 * Peran Pimpinan (PIMP). Bisa melihat laporan dan data secara keseluruhan cabang,
	 * tapi tidak bisa mengubah atau menghapus data apapun.
	 */
	PIMP,

	/**
	 * Peran Administrator. Punya akses penuh ke seluruh fitur sistem, termasuk
	 * manajemen pengguna dan pembaruan database.
	 */
	ADMIN
}
