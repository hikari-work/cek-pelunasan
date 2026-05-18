package org.cekpelunasan.utils;

import org.springframework.stereotype.Service;

/**
 * Menyimpan template pesan-pesan sistem yang dipakai berulang kali di bot Telegram.
 * <p>
 * Daripada mendefinisikan teks pesan langsung di dalam kode handler, semua pesan
 * sistem yang bersifat umum dikumpulkan di sini agar mudah diubah kalau ada
 * perubahan teks tanpa harus cari ke mana-mana.
 * Ini mencakup pesan selamat datang, penolakan akses, hingga pesan format perintah salah.
 * </p>
 */
@Service
public class MessageTemplate {

	/**
	 * Pesan konfirmasi ketika pengguna baru berhasil terdaftar ke bot.
	 *
	 * @return teks pesan konfirmasi pendaftaran berhasil
	 */
	public String authorizedMessage() {
		return """
			✨ Yeay! Pendaftaran kamu berhasil! 🎉
			""";
	}

	/**
	 * Pesan penolakan akses untuk pengguna yang belum terdaftar.
	 * Menginstruksikan pengguna untuk mengirim ID mereka ke admin untuk didaftarkan.
	 *
	 * @return teks pesan penolakan akses
	 */
	public String unathorizedMessage() {
		return """
			🚫 *Akses Ditolak* 🚫
			
			Kamu belum terdaftar untuk menggunakan bot ini. \s
			Gunakan perintah `/id`, lalu kirim hasilnya ke bot. \s
			Tunggu konfirmasi akses dari admin.
			
			""";
	}
	/**
	 * Pesan error ketika pengguna memasukkan format perintah yang salah.
	 * Mengarahkan pengguna untuk membaca panduan di menu Help.
	 *
	 * @return teks pesan format perintah salah
	 */
	public String notValidDeauthFormat() {
		return """
			⚠️ Format salah.
			
			Format yang anda masukkan untuk command
			tersebut salah, silakan mengunjungi Help untuk informasi
			lebih lanjut.
			""";
	}

	/**
	 * Pesan error ketika pengguna memasukkan ID yang bukan angka.
	 *
	 * @return teks pesan validasi ID harus angka
	 */
	public String notValidNumber() {
		return """
			❌ ID harus berupa angka.
			
			ID yang anda masukkan harus berupa Angka
			Parameter yang anda masukkan tidak dapat dikenali.
			""";
	}
}
