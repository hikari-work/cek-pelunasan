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

# Run lokal (set env dulu)
export $(grep -v '^#' .env | xargs) && make run

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

| Modul                          | Status |
|---|---|
| Config + entity + repository   | ✅ |
| Service: users, auth, log, bill, hotkolek, savings, credithistory, paymentdetails, kolektas, simulasiangsuran, minbunga | ✅ |
| Mini App (Fiber, init-data verify) | ✅ |
| HTTP server + actuator         | ✅ |
| Telegram bot fondasi           | ✅ |
| Telegram command handler       | 🚧 20/28 (start, id, help, status, auth, otor, app, tagih, dauth, owner, kantor, sim, cariNasabah, tab, canvas, canvasing, jb, pabpr, kolektas, broadcast — sisanya stub) |
| Telegram callback handler      | 🚧 11/20 (none, branch, paging, tagihan, savingsBranch, savingsNext, canvas, namaTagihan, tagihNext, minimalpay, kolektas — sisanya stub) |
| WhatsApp sender + webhook + router | ✅ |
| WhatsApp handler per fitur     | 🚧 stub semua (.p, .t, .slik, .va, .jb, .minbunga, .email, hot kolek) |
| SLIK PDF (wkhtmltopdf)         | ⏳ pending |
| Telegram CSV upload (bills/tab/payment/history) | ⏳ pending |

## Lisensi

MIT
