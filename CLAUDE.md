# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
mvn clean package

# Run tests
mvn clean test

# Run single test class
mvn test -Dtest=GenerateTest

# Run the application
java -jar target/cek-pelunasan-2.0.0.jar
```

## Architecture Overview

This is a **Spring Boot 3.4.4 / Java 21 Telegram bot** for managing loan settlement (pelunasan) data in an Indonesian financial/credit context.

### Core Flow

1. Telegram updates arrive via **long polling** (`LongPollingBot`) or **webhook** (`WebhookController`)
2. `TelegramBot` routes messages/callbacks to the appropriate handler
3. `CommandHandler` dispatches text commands to one of ~29 `CommandProcessor` implementations
4. Handlers call services → repositories → MySQL database

### Key Packages

- `handler/command/handler/` — 29 individual command handlers (e.g., `/auth`, `/help`, `/simulasi`)
- `handler/callback/` — Inline keyboard callback handlers
- `service/Bill/` — Bill retrieval, CSV import with batch/virtual-thread processing
- `service/simulasi/` — Payment simulation: calculates installments, interest vs. principal sequencing, late payment logic (90-day threshold)
- `service/slik/` — Generates PDF documents from HTML templates using iText/Flying Saucer, reads PDFs via PDFBox
- `service/auth/AuthorizedChats` — Authorization cache (`ConcurrentHashMap`), pre-loaded at startup, enforced via `@RequireAuth` AOP aspect

### Important Design Patterns

**Authorization via AOP:** Methods annotated with `@RequireAuth` are intercepted by `AuthorizationAspect`. User authorization state is cached in `AuthorizedChats` and pre-loaded via `ApplicationReadyEvent`.

**Command Pattern:** All Telegram commands implement `CommandProcessor`. Register new commands by adding a handler class and wiring it into `CommandHandler`'s processor map.

**Async with Virtual Threads:** `AsyncConfiguration` sets up a virtual thread executor. CSV imports and I/O-heavy operations use `@Async` to avoid blocking.

**Storage:** Cloudflare R2 (S3-compatible) via AWS SDK v2 — configured in `S3ClientConfiguration`. Used for SLIK PDF storage.

### Domain Entities

- **Bills** (`SPK` as PK) — loan contracts with financial fields: plafond, debit tray, interest, principal, penalties, days late
- **User** — Telegram chat ID, branch code, `AccountOfficerRoles`
- **Simulasi** — payment simulation state: late days, interest/principal sequence, outstanding amounts
- **CreditHistory / CustomerHistory / KolekTas / Savings / Payment** — supporting financial records

### Configuration

All configuration is via environment variables (no `application.properties` committed). Required env vars:

| Category | Variables |
|----------|-----------|
| Database | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `MAX_POOL` |
| Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_OWNER`, `WEBHOOK_MODE` |
| Cloudflare R2 | `R2_ACCOUNT_ID`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`, `R2_ENDPOINT`, `R2_BUCKET_NAME` |
| WhatsApp | `WHATSAPP_GATEWAY_URL`, `WHATSAPP_GATEWAY_USERNAME`, `WHATSAPP_GATEWAY_PASSWORD`, `ADMIN_WHATSAPP` |
| Server | `PORT` |

### Deployment

Docker multi-stage build: Maven build on `amazoncorretto:21` → runtime on `amazoncorretto:21-al2023-jdk`. Uses Shenandoah GC, 512MB memory limit. Configure via `.env` file alongside `docker-compose.yml`.
