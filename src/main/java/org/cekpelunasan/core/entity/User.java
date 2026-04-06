package org.cekpelunasan.core.entity;

import jakarta.annotation.Nullable;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Data pengguna yang terdaftar dan diizinkan menggunakan bot Telegram ini.
 * <p>
 * Tidak semua orang yang chat ke bot langsung bisa pakai fiturnya. Setiap pengguna
 * harus didaftarkan terlebih dahulu oleh admin, lengkap dengan kode AO dan cabangnya.
 * Class ini menyimpan data registrasi tersebut, dengan chat ID Telegram sebagai
 * kunci utama.
 * </p>
 * <p>
 * Koleksi MongoDB yang dipakai adalah {@code users}.
 * </p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "users")
public class User {

	/**
	 * Chat ID Telegram pengguna ini — angka unik yang diberikan Telegram ke setiap
	 * akun, sekaligus menjadi primary key dokumen di MongoDB. Dipakai untuk
	 * mengirim notifikasi langsung ke pengguna.
	 */
	@Id
	private Long chatId;

	/**
	 * Kode AO (Account Officer) pengguna ini sesuai sistem core banking.
	 * Boleh null jika pengguna belum dikonfigurasi penuh atau berstatus ADMIN.
	 */
	@Nullable
	private String userCode;

	/**
	 * Kode cabang tempat pengguna ini bertugas, misalnya "1075".
	 * Dipakai untuk memfilter data yang tampil agar sesuai cabang masing-masing.
	 * Boleh null jika belum dikonfigurasi.
	 */
	@Nullable
	private String branch;

	/**
	 * Peran pengguna dalam sistem, menentukan fitur apa saja yang bisa diakses.
	 * Lihat {@link AccountOfficerRoles} untuk daftar lengkap peran yang tersedia.
	 * Boleh null jika pengguna belum diberi peran.
	 */
	@Nullable
	private AccountOfficerRoles roles;

}
