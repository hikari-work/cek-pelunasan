# Bot Cek Pelunasan

Bot Telegram & WhatsApp untuk manajemen data pelunasan kredit, tabungan, dan koleksi di lingkup koperasi/BPR.

## Tech Stack

- **Runtime:** Go 1.23+
- **HTTP Framework:** [Fiber v2](https://github.com/gofiber/fiber)
- **Database:** MongoDB (mongo-driver v2)
- **Telegram Bot:** [go-telegram-bot-api/v5](https://github.com/go-telegram-bot-api/telegram-bot-api)
- **WhatsApp:** [whatsmeow](https://github.com/tulir/whatsmeow) (native library, multi-device API)
- **Logging:** stdlib `log/slog` (JSON structured logging)
- **Metrics:** [prometheus/client_golang](https://github.com/prometheus/client_golang)
- **PDF Generation:** wkhtmltopdf (untuk laporan SLIK)
- **Object Storage:** Cloudflare R2 (S3-compatible)
- **Email:** SMTP (stdlib `net/smtp`)

## Prerequisites

### 1. Install Go

**Linux/macOS:**
```bash
# Download dan install Go 1.23+
wget https://go.dev/dl/go1.23.0.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go1.23.0.linux-amd64.tar.gz

# Tambahkan ke PATH
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc

# Verifikasi instalasi
go version
```

**Windows:**
Download installer dari [go.dev/dl](https://go.dev/dl/) dan jalankan.

### 2. Install MongoDB

**Linux (Ubuntu/Debian):**
```bash
# Import MongoDB public GPG key
curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | \
   sudo gpg -o /usr/share/keyrings/mongodb-server-7.0.gpg --dearmor

# Add MongoDB repository
echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | \
   sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# Install MongoDB
sudo apt-get update
sudo apt-get install -y mongodb-org

# Start MongoDB service
sudo systemctl start mongod
sudo systemctl enable mongod

# Verifikasi
mongosh --eval 'db.runCommand({ connectionStatus: 1 })'
```

**macOS:**
```bash
brew tap mongodb/brew
brew install mongodb-community@7.0
brew services start mongodb-community@7.0
```

**Docker (alternatif):**
```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  -v mongodb_data:/data/db \
  mongo:7.0
```

### 3. Install wkhtmltopdf (untuk PDF SLIK)

**Linux:**
```bash
sudo apt-get install -y wkhtmltopdf
```

**macOS:**
```bash
brew install wkhtmltopdf
```

## Installation

### 1. Clone Repository

```bash
git clone https://github.com/hikari-work/cek-pelunasan.git
cd cek-pelunasan
```

### 2. Install Dependencies

```bash
go mod download
```

### 3. Configuration

Buat file `.env` di root directory:

```env
# MongoDB (REQUIRED)
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/cek_pelunasan

# Telegram Bot (REQUIRED)
TELEGRAM_BOT_TOKEN=your_bot_token_from_botfather
TELEGRAM_BOT_OWNER=your_telegram_chat_id

# WhatsApp (whatsmeow native)
WA_DB_PATH=./data/wa.db
WA_DEVICE_NAME=Bot Cek Pelunasan
WA_LOG_LEVEL=INFO
ADMIN_WHATSAPP=628123456789
WA_COMMAND_PREFIX=.
WA_ALLOW_SELF_MESSAGES=false
EMAIL_FORWARD_RECIPIENT=recipient@example.com
EMAIL_FORWARD_FROM=sender@example.com

# Cloudflare R2 (untuk storage SLIK)
R2_ACCOUNT_ID=your_account_id
R2_ACCESS_KEY=your_access_key
R2_SECRET_KEY=your_secret_key
R2_ENDPOINT=https://your-account.r2.cloudflarestorage.com
R2_BUCKET=your_bucket_name

# Email (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=465
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
EMAIL_FORWARD_RECIPIENT=recipient@example.com
EMAIL_FORWARD_FROM=sender@example.com

# PDF Generation (SLIK)
PDF_ENDPOINT_URL=http://your-php-endpoint/generate
PDF_LOGO_URL=https://your-domain.com/logo.png
SLIK_PDF_MAX_SIZE=5242880000
SLIK_SEARCH_TIMEOUT_SECONDS=30
SLIK_MAX_RESULTS=50

# Mini App
MINIAPP_URL=https://your-domain.com
MINIAPP_SESSION_TTL_MINUTES=60

# Server
SERVER_PORT=8080
```

**Environment Variables Wajib:**
- `SPRING_DATA_MONGODB_URI` - MongoDB connection string
- `TELEGRAM_BOT_TOKEN` - Token dari [@BotFather](https://t.me/botfather)

### 4. Build

```bash
# Build binary
make build

# Atau manual
go build -o bin/cekpelunasan ./cmd/cekpelunasan
```

### 5. Run

```bash
# Run dengan auto-load .env
make run

# Atau jalankan binary langsung
./bin/cekpelunasan

# Atau dengan go run
go run ./cmd/cekpelunasan
```

Server akan berjalan di `http://localhost:8080`

### 6. WhatsApp Pairing (First Time)

Saat pertama kali dijalankan, bot akan menampilkan QR code di terminal:

```bash
INFO whatsapp: belum pair, mulai QR flow
INFO whatsapp: scan QR di HP — WhatsApp → Linked devices → Link a device
```

**Cara pairing:**
1. Buka WhatsApp di HP
2. Tap menu (⋮) → **Linked devices**
3. Tap **Link a device**
4. Scan QR code yang muncul di terminal
5. Tunggu sampai muncul: `INFO whatsapp: paired`

Session akan tersimpan di `./data/wa.db` (SQLite). Restart berikutnya tidak perlu scan QR lagi.

## Project Structure

```
cek-pelunasan/
├── cmd/
│   └── cekpelunasan/          # Main entry point
├── internal/
│   ├── config/                # Environment configuration
│   ├── entity/                # MongoDB models
│   ├── repository/            # Database repositories
│   ├── service/               # Business logic
│   │   ├── bill/              # Tagihan service
│   │   ├── savings/           # Tabungan service
│   │   ├── kolektas/          # Kolek Tas service
│   │   ├── slik/              # SLIK service
│   │   ├── email/             # Email service
│   │   └── ...
│   ├── platform/
│   │   ├── telegram/          # Telegram bot handlers
│   │   ├── whatsapp/          # WhatsApp handlers
│   │   └── r2/                # R2 storage client
│   ├── miniapp/               # REST API untuk Mini App
│   ├── httpserver/            # HTTP server setup
│   └── utils/                 # Helper functions
├── web/
│   └── static/                # Static assets untuk Mini App
├── .env                       # Environment variables
├── Makefile                   # Build commands
└── README.md
```

## API Endpoints

### Mini App (REST API)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/mini/auth` | Authenticate Telegram Mini App |
| `GET` | `/api/mini/tagihan/search` | Search tagihan by name/branch |
| `GET` | `/api/mini/tagihan/:spk` | Get tagihan by SPK |
| `GET` | `/api/mini/tabungan/search` | Search tabungan by name/branch |
| `GET` | `/api/mini/tabungan/:cif` | Get tabungan by CIF |
| `GET` | `/api/mini/canvas/search` | Search canvasing tabungan |
| `GET` | `/api/mini/kolektas/search` | Search Kolek Tas by kelompok |
| `GET` | `/api/mini/kolektas/:id` | Get Kolek Tas by ID |
| `GET` | `/api/mini/payment/search` | Search payment details |
| `GET` | `/api/mini/payment/:spk` | Get payment history by SPK |
| `GET` | `/api/mini/pelunasan/:spk` | Calculate pelunasan for SPK |

### WhatsApp Webhook

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v2/whatsapp` | Webhook untuk WhatsApp gateway |

### Actuator (Monitoring)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/actuator/health` | Health check endpoint |
| `GET` | `/actuator/info` | Application info |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

### Static Assets

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Serve Mini App static files |

## Bot Commands

### Telegram Commands

| Command | Description |
|---------|-------------|
| `/start` | Mulai bot dan tampilkan welcome message |
| `/id` | Tampilkan chat ID Telegram |
| `/help` | Tampilkan daftar command |
| `/auth` | Authorize user untuk akses bot |
| `/status` | Cek status server dan database |
| `/tagih` | Cari tagihan by branch/AO |
| `/tab` | Cari tabungan by name/branch |
| `/canvas` | Canvasing tabungan |
| `/jb` | Reminder jatuh bayar harian |
| `/kolektas` | Cari Kolek Tas by kelompok |
| `/sim` | Simulasi angsuran |
| `/minbunga` | Minimal bunga by branch/AO |
| `/minimalpay` | Minimal payment by branch |
| `/slik` | Search dan generate PDF SLIK |
| `/doc` | Download dokumen SLIK |
| `/uploadtagihan` | Upload CSV tagihan |
| `/uploadtab` | Upload CSV tabungan |
| `/uploadtas` | Upload CSV Kolek Tas |
| `/uploadpayment` | Upload CSV payment details |
| `/uploadcredit` | Upload CSV credit history |
| `/app` | Buka Telegram Mini App |
| `/broadcast` | Broadcast message ke semua user (admin only) |

### WhatsApp Commands

| Command | Description |
|---------|-------------|
| `.p {SPK}` | Cek pelunasan by SPK (12 digit) |
| `.t {nomor/nama}` | Cari tabungan by nomor/nama |
| `.va {SPK}` | Cek virtual account by SPK |
| `.va {rekening}` | Cek virtual account by rekening tabungan |
| `.jb` | Reminder jatuh bayar harian |
| `.minbunga {cabang} {tanggal}` | Minimal bunga (admin only) |
| `.slik {nama}` | Generate dan kirim PDF SLIK |
| `.email` | Mulai session forward media ke email |
| `.done` | Kirim email dengan media yang sudah dikumpulkan |
| `.{12 digit SPK}` | Hot kolek - mark SPK as paid |

**Admin Shortcuts:**
- `/coba`, `/kasih`, `/tunggu`, `/relog`, `/selesai`, `/enter`, `/input`, `/display`, `/terima`

## Docker Deployment

### Build Image

```bash
docker build -t cek-pelunasan:latest .
```

### Run Container

```bash
docker run -d \
  --name cek-pelunasan \
  --env-file .env \
  -p 8080:8080 \
  cek-pelunasan:latest
```

### Docker Compose

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:7.0
    container_name: mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    environment:
      MONGO_INITDB_DATABASE: cek_pelunasan

  cek-pelunasan:
    build: .
    container_name: cek-pelunasan
    ports:
      - "8080:8080"
    env_file:
      - .env
    depends_on:
      - mongodb
    restart: unless-stopped

volumes:
  mongodb_data:
```

Run dengan:
```bash
docker-compose up -d
```

## Systemd Service

Untuk production deployment dengan systemd:

```bash
# Buat service file
sudo nano /etc/systemd/system/cek-pelunasan.service
```

```ini
[Unit]
Description=Bot Cek Pelunasan
After=network.target mongod.service

[Service]
Type=simple
User=your_user
WorkingDirectory=/path/to/cek-pelunasan
Environment="ENV_FILE=/path/to/.env"
ExecStart=/path/to/cek-pelunasan/bin/cekpelunasan
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# Enable dan start service
sudo systemctl daemon-reload
sudo systemctl enable cek-pelunasan
sudo systemctl start cek-pelunasan

# Check status
sudo systemctl status cek-pelunasan

# View logs
sudo journalctl -u cek-pelunasan -f
```

## Development

### Run Tests

```bash
make test

# Atau manual
go test ./...
```

### Code Quality

```bash
# Vet
go vet ./...

# Format
go fmt ./...

# Lint (jika golangci-lint terinstall)
golangci-lint run
```

### Hot Reload (Development)

Install [air](https://github.com/cosmtrek/air):
```bash
go install github.com/cosmtrek/air@latest
```

Run dengan hot reload:
```bash
air
```

## Monitoring

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "uptime": "2h30m15s"
}
```

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

Metrics yang tersedia:
- Go runtime metrics (goroutines, memory, GC)
- Process metrics (CPU, memory, file descriptors)
- HTTP request metrics (jika diaktifkan)

## Troubleshooting

### Bot tidak merespon

1. Cek apakah service berjalan:
   ```bash
   systemctl status cek-pelunasan
   ```

2. Cek logs:
   ```bash
   journalctl -u cek-pelunasan -n 100
   ```

3. Verifikasi MongoDB connection:
   ```bash
   mongosh $SPRING_DATA_MONGODB_URI --eval 'db.runCommand({ ping: 1 })'
   ```

4. Test Telegram bot token:
   ```bash
   curl https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getMe
   ```

### WhatsApp tidak terkoneksi

1. Cek apakah session masih valid:
   ```bash
   # Cek log untuk "whatsapp: connected"
   journalctl -u cek-pelunasan -n 100 | grep whatsapp
   ```

2. Jika logged out, hapus session dan pair ulang:
   ```bash
   rm -f ./data/wa.db
   # Restart service, QR code akan muncul di log
   sudo systemctl restart cek-pelunasan
   sudo journalctl -u cek-pelunasan -f
   ```

3. Cek WhatsApp di HP:
   - Buka WhatsApp → Linked devices
   - Pastikan device "Bot Cek Pelunasan" masih terdaftar
   - Jika tidak ada, pair ulang dengan scan QR

4. Verifikasi file session:
   ```bash
   ls -lh ./data/wa.db
   # File harus ada dan > 0 bytes
   ```

### MongoDB connection error

1. Cek MongoDB service:
   ```bash
   sudo systemctl status mongod
   ```

2. Verifikasi connection string di `.env`

3. Test connection:
   ```bash
   mongosh $SPRING_DATA_MONGODB_URI
   ```

## Performance

### Optimizations

- **Parallel I/O:** Find + Count queries dijalankan paralel menggunakan `errgroup`
- **Connection Pooling:** MongoDB connection pool (MaxPoolSize=40, MinPoolSize=5)
- **Context Timeout:** Semua database operations dengan context timeout
- **Structured Logging:** JSON logging dengan `slog` untuk efficient parsing

### Benchmarks

Typical response times (local development):
- Health check: ~1ms
- Simple query (by ID): ~10-20ms
- Paginated query (Find + Count): ~100ms (parallel)
- PDF generation (SLIK): ~2-5s

## Contributing

1. Fork repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

MIT License - see [LICENSE](LICENSE) file for details

## Support

Untuk pertanyaan atau issue, buka [GitHub Issues](https://github.com/hikari-work/cek-pelunasan/issues)
