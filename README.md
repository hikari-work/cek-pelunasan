# 🤖 Bot Cek Pelunasan

Bot Telegram untuk memudahkan pengecekan informasi pelunasan nasabah, termasuk status tagihan, denda, dan data Nasabah. Dibangun dengan Java dan Spring Boot, bot ini terintegrasi dengan database internal untuk memberikan informasi secara real-time.

## ✨ Fitur Utama

- 🔍 **Pencarian Nasabah** berdasarkan nama atau nomor kontrak.
- 📄 **Detail Informasi**: Menampilkan SPK, nama, alamat, produk, plafon, baki debet, tunggakan, denda, dan total tagihan.
- 📊 **Statistik Sistem**: Menampilkan load system dan status koneksi database.
- 🛡️ **Akses Terbatas**: Hanya pengguna yang terdaftar yang dapat menggunakan bot.
- 🔀 **Perintah Bantuan**: `/help` untuk menampilkan daftar perintah yang tersedia.

## 🚀 Cara Deploy

### 1. Persiapan

- Pastikan Java 17+ dan Maven terinstal di sistem Anda.
- Siapkan database MySql dengan skema yang sesuai.
- Jika menggunakan PostgreSQL silahkan edit Driver sendiri di pom.xml

### 2. Konfigurasi

- Salin file `application.properties.example` menjadi `application.properties`.
- Isi konfigurasi berikut:

```properties
telegram.bot.token=YOUR_TELEGRAM_BOT_TOKEN
telegram.bot.username=YOUR_BOT_USERNAME
spring.datasource.url=jdbc:postgresql://localhost:5432/cek_pelunasan
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
```

### 3. Build dan Jalankan

- Bangun proyek:

```bash
mvn clean package
```

- Jalankan aplikasi:

```bash
java -jar target/cek-pelunasan-0.0.1-SNAPSHOT.jar
```

### 4. Daftarkan Bot di Telegram

- Cari `@BotFather` di Telegram.
- Gunakan perintah `/newbot` untuk membuat bot baru dan dapatkan token akses.
- Masukkan token tersebut ke dalam konfigurasi `application.properties`.

## 🛠️ Pengembangan

- Struktur proyek mengikuti standar Spring Boot.
- Gunakan `TelegramBots` library untuk integrasi dengan Telegram API.
- Database diakses menggunakan Spring Data JPA.
- Silahkan Pull Request untuk pengembangan BOT ini

## 📄 Lisensi

Proyek ini dilisensikan di bawah MIT License. Lihat file [LICENSE](LICENSE) untuk informasi lebih lanjut.
