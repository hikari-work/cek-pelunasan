package org.cekpelunasan.handler.command.template;

import org.springframework.stereotype.Service;

@Service
public class MessageTemplate {

    public String authorizedMessage() {
        return """
                🎉 Selamat datang! Kamu berhasil terdaftar untuk menggunakan bot ini.
                
                💍 Nih, cincinnya buat kamu~ \s
                Kepoin cara pakainya lewat /help ya!
                
                """;
    }
    public String unathorizedMessage() {
        return """
                🚫 *Akses Ditolak* 🚫
                
                Kamu belum terdaftar untuk menggunakan bot ini. \s
                Gunakan perintah `.id`, lalu kirim hasilnya ke bot. \s
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

    public String helpMessage() {
        return """
        🆘 *Panduan Penggunaan Bot Pelunasan* 🆘

        Berikut ini adalah daftar perintah yang dapat kamu gunakan:

        🔹 */pl [nomor]* — Cari nasabah berdasarkan nomor SPK.
        Contoh: `/pl 117204000345`

        🔹 */fi [nama]* — Cari nasabah berdasarkan Nama.
        Contoh: `/fi Budi`

        🔹 *Next dan Prev* — Navigasi halaman hasil pencarian.
        Gunakan setelah pencarian untuk pindah halaman.
        
        🔹 */tagih* — Tampilkan tagihan, pencarian Berdasarkan No Spk
        
        🔹 */tgnama * — Tampilkan tagihan berdasarkan Nama.

        🔹 */status* — Tampilkan status bot, termasuk load sistem dan koneksi database.

        🔹 */help* — Tampilkan pesan bantuan ini.

        ℹ️ *Catatan*: Gunakan kata kunci yang spesifik untuk hasil pencarian terbaik.
        
        🔐 Data yang ditampilkan bersifat pribadi. Gunakan dengan bijak.

        🙏 Terima kasih telah menggunakan Pelunasan Bot!
        """;
    }
}
