package utils

// MessageTemplate berisi pesan-pesan sistem standar yang dipakai handler bot.
// Padanan dari MessageTemplate.java — diubah jadi konstanta karena tidak ada
// state yang perlu di-inject (Spring @Service di Java cuma boilerplate).
const (
	MsgAuthorizedSuccess = "✨ Yeay! Pendaftaran kamu berhasil! 🎉\n"

	MsgUnauthorized = "🚫 *Akses Ditolak* 🚫\n\n" +
		"Kamu belum terdaftar untuk menggunakan bot ini.  \n" +
		"Gunakan perintah `/id`, lalu kirim hasilnya ke bot.  \n" +
		"Tunggu konfirmasi akses dari admin.\n\n"

	MsgInvalidDeauthFormat = "⚠️ Format salah.\n\n" +
		"Format yang anda masukkan untuk command\n" +
		"tersebut salah, silakan mengunjungi Help untuk informasi\n" +
		"lebih lanjut.\n"

	MsgIDMustBeNumber = "❌ ID harus berupa angka.\n\n" +
		"ID yang anda masukkan harus berupa Angka\n" +
		"Parameter yang anda masukkan tidak dapat dikenali.\n"
)
