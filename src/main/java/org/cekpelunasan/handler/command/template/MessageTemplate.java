package org.cekpelunasan.handler.command.template;

import org.springframework.stereotype.Service;

@Service
public class MessageTemplate {

	public String authorizedMessage() {
		return """
			✨ Yeay! Pendaftaran kamu berhasil! 🎉
			
			🤖 Bot ini sekarang resmi jadi partner kamu~
			💍 Nih, cincin virtualnya... *cling* 💫
			
			Mau tau jurus-jurus rahasianya? Langsung cek /help ya! 📜
			
			Selamat berpetualang! 🚀
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

	public String notAdminUsers() {
		return """
			❌ Kamu tidak punya izin untuk perintah ini.
			
			
			Pastikan kamu adalah pemilik bot ini, Jika Benar
			silahkan konfigurasi ulang...
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

	public String fiCommandHelper() {
		return """
			⚠️ *Informasi* ⚠️
			
			Command `/fi` digunakan untuk mencari nasabah berdasarkan nama.
			Contoh: `/fi Budi`
			""";
	}

}
