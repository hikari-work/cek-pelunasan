# Bot Cek Pelunasan (Go)

Bot Telegram & WhatsApp untuk manajemen data pelunasan kredit di lingkup koperasi/BPR.

> **Status migrasi (2026-05-18):** Codebase sedang dimigrasikan dari Spring Boot/Java
> ke Go murni di branch `feat/migrate-golang`. Source Java lama dipertahankan di
> folder [`legacy/`](./legacy) sebagai referensi sampai migrasi selesai.
>
> Perintah/fitur yang belum diport akan membalas pesan
> "fitur sedang dimigrasikan" — bot tetap responsif tapi belum lengkap.

## Tech Stack (versi Go)

- **Runtime:** Go 1.23+
- **HTTP:** [Fiber v2](https://github.com/gofiber/fiber)
- **Telegram:** [go-telegram-bot-api/v5](https://github.com/go-telegram-bot-api/telegram-bot-api) (Bot API, bukan TDLib)
- **MongoDB:** [mongo-driver/v2](https://github.com/mongodb/mongo-go-driver) resmi
- **Logging:** stdlib `log/slog` (JSON handler)
- **Metrics:** [prometheus/client_golang](https://github.com/prometheus/client_golang)
- **WhatsApp Gateway:** [go-whatsapp-web-multidevice](https://github.com/aldinokemal2104/go-whatsapp-web-multidevice) (eksternal, HTTP)
- **PDF (rencana SLIK):** wkhtmltopdf (binary terpisah, belum di-wire)

## Layout Project

```
cmd/cekpelunasan/    # entry point
internal/
  config/            # env loader
  entity/            # MongoDB models
  repository/        # repo per koleksi (mongo-driver v2)
  service/           # logic per domain (bill, savings, kolektas, slik, ...)
  utils/             # helper generik (rupiah, phone, date, system)
  miniapp/           # REST API Mini App via Fiber
  platform/
    telegram/        # bot wrapper + router + handler
    whatsapp/        # sender + webhook + router
  httpserver/        # Fiber wiring + actuator (health/info/prometheus)
web/static/          # static assets Mini App
legacy/              # source Java/Maven lama (read-only)
```

## Konfigurasi

Semua via environment variable, kompatibel dengan `.env` lama:

```env
SPRING_DATA_MONGODB_URI=mongodb+srv://user:pass@host/db

TELEGRAM_BOT_TOKEN=your_token
TELEGRAM_BOT_OWNER=your_chat_id

R2_ACCOUNT_ID=
R2_ACCESS_KEY=
R2_SECRET_KEY=
R2_ENDPOINT=
R2_BUCKET=

WHATSAPP_GATEWAY_URL=http://localhost:3000
WHATSAPP_GATEWAY_USERNAME=admin
WHATSAPP_GATEWAY_PASSWORD=password
ADMIN_WHATSAPP=628123456789
WA_COMMAND_PREFIX=.

MAIL_HOST=smtp.sumopod.com
MAIL_PORT=465
MAIL_USERNAME=
MAIL_PASSWORD=
EMAIL_FORWARD_RECIPIENT=
EMAIL_FORWARD_FROM=

PDF_ENDPOINT_URL=
PDF_LOGO_URL=
SLIK_PDF_MAX_SIZE=5242880000
SLIK_SEARCH_TIMEOUT_SECONDS=30
SLIK_MAX_RESULTS=50

MINIAPP_URL=https://your-domain.com
MINIAPP_SESSION_TTL_MINUTES=60

SERVER_PORT=8080
```

Hanya `TELEGRAM_BOT_TOKEN` dan `SPRING_DATA_MONGODB_URI` yang wajib (lihat `Validate()`).

## Build & Run

```bash
# Build static binary
make build

# Run lokal — binary auto-load .env kalau ada di working directory.
# Set ENV_FILE=path/to/.env untuk override lokasi.
make run

# Alternatif: export var .env dulu sebelum go run (berguna kalau child
# process seperti wkhtmltopdf perlu env yang sama).
make run-env

# Test + vet + lint
make test
go vet ./...
golangci-lint run        # opsional, kalau golangci-lint terpasang
```

Binary single-file ada di `bin/cekpelunasan`.

## Docker

```bash
docker build -t cek-pelunasan:go .
docker run --rm --env-file .env -p 8080:8080 cek-pelunasan:go
```

Image final pakai `distroless/static` — minimal attack surface, tanpa shell/libc.

## Endpoint

| Path                       | Deskripsi |
|---|---|
| `POST /api/mini/auth`      | Verifikasi initData Telegram, keluarkan session token |
| `GET  /api/mini/tagihan/*` | Mini App: pencarian tagihan |
| `GET  /api/mini/tabungan/*`| Mini App: pencarian tabungan |
| `GET  /api/mini/canvas/*`  | Mini App: canvasing tabungan |
| `GET  /api/mini/kolektas/*`| Mini App: Kolek Tas |
| `GET  /api/mini/payment/*` | Mini App: detail angsuran |
| `GET  /api/mini/pelunasan/*`| Mini App: detail pelunasan (parsial) |
| `POST /v2/whatsapp`        | Webhook gateway WhatsApp |
| `GET  /actuator/health`    | Health check (UP + uptime) |
| `GET  /actuator/info`      | Info app + runtime |
| `GET  /actuator/prometheus`| Metrics Prometheus (Go runtime + process) |
| `GET  /`                   | Static assets Mini App |

## Status Migrasi per Modul

Semua modul sudah diport ke Go. Tabel di bawah adalah checklist
verifikasi manual — tandai ✅ kalau sudah dites end-to-end di
environment yang dipakai user.

### Telegram

| Command / Callback | Status QA |
|---|---|
| `/start`, `/id`, `/help` | ☐ |
| `/auth`, `/dauth`, `/otor`, `/owner` | ☐ |
| `/status` | ☐ |
| `/tagih`, callback paging tagihan + branch picker | ☐ |
| `/tab`, `/canvas`, `/canvasing` (callback paging savings) | ☐ |
| `/jb` (jatuh bayar harian) | ☐ |
| `/kolektas`, callback paging kolektas | ☐ |
| `/sim` (simulasi angsuran), `/cariNasabah` | ☐ |
| `/kantor`, `/broadcast` | ☐ |
| `/minbunga` (calendar picker + branch + AO flow) | ☐ |
| `/minimalpay` | ☐ |
| `/slik` (NIK / nama search + month picker) | ☐ |
| `/doc` (ambil dokumen SLIK by name) | ☐ |
| Upload SLIK via document handler | ☐ |
| `/uploadtagihan`, `/uploadtab`, `/uploadtas`, `/uploadpayment`, `/uploadcredit` | ☐ |
| `/app` (Mini App) | ☐ |
| Services callback (Pelunasan / Tabungan picker) | ☐ |

### WhatsApp

| Perintah | Status QA |
|---|---|
| Shortcut admin (`/coba`, `/kasih`, `/tunggu`, `/relog`, `/selesai`, `/enter`, `/input`, `/display`, `/terima`) | ✅ |
| `.p {SPK}` — pelunasan | ☐ |
| `.t {nomor / nama}` — tabungan | ☐ |
| `.va {SPK / rekening}` — virtual account dari bills | ✅ |
| `.va {rekening}` — virtual account dari savings | ✅ |
| `.jb` — reminder jatuh bayar harian | ☐ |
| `.minbunga {cabang} {tanggal}` — admin only | ✅ |
| `.slik {nama}` — kirim PDF asli + 2 generated | ✅ |
| `.email` / `.done` — forward media ke email via SMTP | ☐ |
| `.NNNNNNNNNNNN` — hot kolek (single + multi SPK) | ✅ |

### Mini App (REST API + UI)

| Endpoint | Status QA |
|---|---|
| `POST /api/mini/auth` (verify Telegram initData) | ☐ |
| `GET /api/mini/tagihan/*` | ☐ |
| `GET /api/mini/tabungan/*` | ☐ |
| `GET /api/mini/canvas/*` | ☐ |
| `GET /api/mini/kolektas/*` | ☐ |
| `GET /api/mini/payment/*` | ☐ |
| `GET /api/mini/pelunasan/*` | ☐ |
| Static assets (`GET /`) | ☐ |

### Infrastruktur

| Item | Status QA |
|---|---|
| MongoDB connect + ObjectID legacy decode | ✅ |
| WhatsApp pairing (whatsmeow native) | ✅ |
| Telegram bot polling | ✅ |
| Owner auto-authorize saat startup | ✅ |
| Custom command prefix (`WA_COMMAND_PREFIX`) | ☐ |
| Single-account dev mode (`WA_ALLOW_SELF_MESSAGES`) | ✅ |
| `.env` auto-load via godotenv | ✅ |
| Actuator: `/actuator/health`, `/info`, `/prometheus` | ☐ |
| CSV import duplicate-key skip (E11000) | ☐ |
| Docker build + run distroless | ☐ |

## Lisensi

MIT
