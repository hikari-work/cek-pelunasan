# 🤖 Bot Cek Pelunasan

Bot Telegram untuk memudahkan pengecekan informasi pelunasan nasabah, termasuk status tagihan, denda, dan data Nasabah. Dibangun dengan Java dan Spring Boot, bot ini terintegrasi dengan database internal untuk memberikan informasi secara real-time.

## ✨ Fitur Utama

- 🔍 **Pencarian Nasabah** berdasarkan nama atau nomor kontrak.
- 📄 **Detail Informasi**: Menampilkan SPK, nama, alamat, produk, plafon, baki debet, tunggakan, denda, dan total tagihan.
- 📊 **Statistik Sistem**: Menampilkan load system dan status koneksi database.
- 🛡️ **Akses Terbatas**: Hanya pengguna yang terdaftar yang dapat menggunakan bot.
- 🎁 **Informasi Tagihan**: Menampilkan tagihan lengkap, dari Pengaruh NPL, Jatuh Bayar.
- 🥽 **Simulasi**: Dapat melakukan Simulasi pembayaran, dan menghitung minimal bayar.
- 🔐 **Sistem Layanan Informasi Keuangan**: Melakukan request untuk mendapatkan dokumen asli dan generate Resume nya, membatasi user melakukan generate.
- 🧷 **Marketing Dana**: User dapat mencari Tabungan dan Kolek Tabungan Berjangka.
- 😎 **GEMINI** : Disertai Inline untuk bertanya lewat AI

## 🚀 Cara Deploy

### 1. Persiapan

- Pastikan Java 21 dan Maven terinstal di sistem Anda.
- Siapkan database MySql dengan skema yang sesuai.
- Jika menggunakan PostgreSQL silahkan edit Driver sendiri di pom.xml

### 2. Konfigurasi

- Salin file `application.properties.example` menjadi `application.properties`.
- Isi konfigurasi berikut:

```properties
telegram.bot.token=TELEGRAM_BOT_TOKEN
telegram.bot.owner=TELEGRAM_BOT_OWNER
spring.datasource.url=DATABASE_URL
server.port=SERVER_PORT
spring.datasource.hikari.maximum-pool-size=MAX_POOL
spring.datasource.hikari.minimum-idle=2
spring.jpa.hibernate.ddl-auto=update
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB
spring.jpa.properties.hibernate.jdbc.batch_size=BATCH_SIZE
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.hibernate.SQL=DEBUG
r2.account.id=R2_ACCOUNT_ID
r2.access.key=R2_ACCESS_ACCOUNT
r2.secret.key=RE_SECRET_KEY
r2.endpoint=r2.cloudflarestorage.com
r2.bucket=R2_BUCKET_NAME
gemini.key=GEMINI_KEY

```

### 3. Build dan Jalankan

- Bangun proyek:

```bash
mvn clean package
```

- Setup Ngrok untuk Menerima Webhook
```bash
ngrok http 9000
```
Sesuaikan dengan port server

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
