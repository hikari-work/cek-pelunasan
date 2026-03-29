package org.cekpelunasan.utils;

import org.springframework.stereotype.Service;

@Service
public class MessageTemplate {

	public String authorizedMessage() {
		return """
			✨ Yeay! Pendaftaran kamu berhasil! 🎉
			""";
	}

	public String unathorizedMessage() {
		return """
			🚫 *Akses Ditolak* 🚫
			
			Kamu belum terdaftar untuk menggunakan bot ini. \s
			Gunakan perintah `/id`, lalu kirim hasilnya ke bot. \s
			Tunggu konfirmasi akses dari admin.
			
			""";
	}
	public String notValidDeauthFormat() {
		return """
			⚠️ Format salah.
			
			Format yang anda masukkan untuk command
			tersebut salah, silakan mengunjungi Help untuk informasi
			lebih lanjut.
			""";
	}

	public String notValidNumber() {
		return """
			❌ ID harus berupa angka.
			
			ID yang anda masukkan harus berupa Angka
			Parameter yang anda masukkan tidak dapat dikenali.
			""";
	}
}
