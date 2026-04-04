# Bot Cek Pelunasan

Bot Telegram & WhatsApp untuk manajemen data pelunasan kredit di lingkup koperasi/BPR. Dibangun dengan Spring Boot 3 + Java 21, menggunakan TDLight (TDLib) sebagai Telegram client dan MongoDB sebagai database utama.

## Fitur

### Telegram
| Command | Deskripsi |
|---|---|
| `/slik <ktp\|nama>` | Cari data SLIK — by 16-digit KTP atau nama (case-insensitive), tampil per halaman dengan Next/Prev. Admin dapat mencari semua usercode. |
| `/tab <nama>` | Cari data tabungan nasabah |
| `/otor <kode>` | Daftarkan diri sebagai AO (3 huruf) atau Pimpinan (kode cabang) |
| `/simulasi` | Simulasi pembayaran kredit |
| `/help` | Daftar semua perintah |
| `/auth` | Autentikasi sesi |

### WhatsApp
| Perintah | Deskripsi |
|---|---|
| `.t <nomor rekening>` | Detail tabungan by nomor rekening (12 digit) |
| `.t <nama>` | Cari tabungan by nama (max 5 hasil) |
| `.p <spk>` | Cek pelunasan kredit |
| `.s <ktp>` | Cek SLIK by nomor KTP |
| `.jb` | Info jatuh bayar |
| `.<12 digit>` | Hot kolek — cek tagihan langsung |

## Tech Stack

- **Runtime:** Java 21 (Virtual Threads), Spring Boot 3.4.4
- **Telegram:** TDLight 3.4.4 (TDLib — bukan Bot API)
- **Database:** MongoDB (Reactive — Spring Data Reactive MongoDB)
- **Storage:** Cloudflare R2 (S3-compatible, AWS SDK v2)
- **WhatsApp Gateway:** [go-whatsapp-web-multidevice](https://github.com/aldinokemal2104/go-whatsapp-web-multidevice)
- **PDF:** Playwright (Chromium headless) + Apache PDFBox
- **Async:** Project Reactor (WebFlux) — tidak ada `.block()` di production path

## Arsitektur

```
Telegram Update (long-poll/webhook)
    └── TelegramBot
        ├── CommandHandler → CommandProcessor (29 handler)
        └── CallbackHandler → CallbackProcessor

WhatsApp Webhook → WebhookController → Routers
    ├── TabunganService  (.t)
    ├── HandlerPelunasan (.p)
    ├── SlikService      (.s)
    ├── JatuhBayarService (.jb)
    └── HandleKolekCommand (.<12digit>)
```

**Authorization:** AOP via `@RequireAuth` — dicek dari `AuthorizedChats` (ConcurrentHashMap, di-load saat startup).

**SLIK name search:** Hasil disimpan di `SlikSessionCache` (TTL 30 menit), navigasi dengan inline keyboard Next/Prev, setiap fasilitas ditampilkan sebagai expandable blockquote.

## Konfigurasi

Semua konfigurasi via environment variable (file `.env`):

```env
# MongoDB
SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@host/db

# Telegram
TELEGRAM_BOT_TOKEN=your_token
TELEGRAM_BOT_OWNER=your_chat_id
TELEGRAM_API_ID=123456
TELEGRAM_API_HASH=your_api_hash

# Cloudflare R2
R2_ACCOUNT_ID=your_account_id
R2_ACCESS_KEY=your_access_key
R2_SECRET_KEY=your_secret_key
R2_ENDPOINT=https://xxx.r2.cloudflarestorage.com
R2_BUCKET=your_bucket

# WhatsApp Gateway
WHATSAPP_GATEWAY_URL=http://localhost:3000
WHATSAPP_GATEWAY_USERNAME=admin
WHATSAPP_GATEWAY_PASSWORD=your_password
WHATSAPP_DEVICE_ID=6281234567890.0:0@s.whatsapp.net
ADMIN_WHATSAPP=628123456789

# Server
SERVER_PORT=8080
```

## Deploy dengan Docker

### Langkah 1 — Jalankan WhatsApp Gateway dulu

```bash
docker compose up -d whatsapp
```

Buka `http://server:3000`, login, scan QR code. Ambil device ID:

```bash
curl -u admin:password http://server:3000/app/devices
```

Salin device ID ke `.env`:
```env
WHATSAPP_DEVICE_ID=6281234567890.0:0@s.whatsapp.net
```

### Langkah 2 — Jalankan aplikasi

```bash
docker compose up -d app
```

### Update aplikasi

```bash
docker compose up -d --build app
```

## Build Lokal

```bash
# Build
mvn clean package

# Run tests
mvn clean test

# Jalankan
java -jar target/cek-pelunasan-2.0.0.jar
```

Untuk development lokal, load `.env` terlebih dahulu:

```bash
export $(grep -v '^#' .env | xargs) && mvn spring-boot:run
```

Atau install plugin **EnvFile** di IntelliJ.

## Lisensi

MIT License
