package org.cekpelunasan.handler.command;

import org.springframework.stereotype.Service;

@Service
public class MessageTemplate {

    public String authorizedMessage() {
        return """
                Selamat, Kamu Diterima untuk menggunakan Bot ini,
                💍 Ini Cincin Untukmu, Cara Pakainya ada di `/help`
                """;
    }
    public String unathorizedMessage() {
        return """
                🚫 *Akses Ditolak* 🚫
                
                Kamu belum terdaftar untuk menggunakan bot ini.
                Minta akses ke Admin untuk bisa menggunakan fitur-fitur yang tersedia.
                Gunakan `.id` Kemudian salin dan kirim ke botnya,
                Tunggu admin membalas untuk konfirmasi...
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

        🔹 */next* dan */prev* — Navigasi halaman hasil pencarian.
        Gunakan setelah pencarian untuk pindah halaman.

        🔹 */status* — Tampilkan status bot, termasuk load sistem dan koneksi database.

        🔹 */help* — Tampilkan pesan bantuan ini.

        ℹ️ *Catatan*: Gunakan kata kunci yang spesifik untuk hasil pencarian terbaik.
        
        🔐 Data yang ditampilkan bersifat pribadi. Gunakan dengan bijak.

        🙏 Terima kasih telah menggunakan Pelunasan Bot!
        """;
    }
}
