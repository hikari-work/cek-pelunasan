---
name: codebase-overview
description: Gambaran umum arsitektur project cek-pelunasan — multi-platform bot koperasi/BPR dengan Telegram dan WhatsApp, berbasis Spring Boot 3.4.4 + Java 21 + MongoDB Reactive
---

# Cek Pelunasan — Codebase Overview

## Identitas Project
- **Versi**: 4.0.0
- **Stack**: Spring Boot 3.4.4, Java 21, Project Reactor (WebFlux)
- **Database**: MongoDB (Reactive Driver via Spring Data)
- **Platform**: Telegram (TDLight/TDLib) + WhatsApp (go-whatsapp-web gateway)
- **Entry Point**: `CekPelunasanApplication.java`

## Tujuan Sistem
Bot hybrid untuk manajemen data pelunasan kredit di lingkup koperasi/BPR:
- Pengecekan SLIK (credit history)
- Simulasi pembayaran pelunasan
- Manajemen tabungan nasabah
- Koleksi tagihan bermasalah
- WebApp Telegram (Mini App) sebagai dashboard

## Struktur Package Utama
```
org.cekpelunasan/
├── annotation/          @RequireAuth
├── aspect/              AuthorizationAspect (AOP)
├── configuration/       Spring config beans
├── controller/          WebhookController (WhatsApp)
├── core/
│   ├── entity/          12 domain entities + 1 enum
│   ├── event/           ApplicationEvent system
│   ├── lifecycle/       Startup hooks
│   ├── repository/      10 Reactive repositories
│   └── service/         15+ service classes
├── miniapp/             Telegram WebApp (REST API)
└── platform/
    ├── telegram/        TDLight bot logic
    └── whatsapp/        Webhook handlers
```

## Lapisan Arsitektur
```
[Telegram TDLight]          [WhatsApp Webhook]
       ↓                           ↓
[CommandHandler/CallbackHandler]  [Routers.java]
       ↓                           ↓
[CommandProcessor / Handler*]   [Handler* classes]
       ↓                           ↓
          [Service Layer]
               ↓
     [ReactiveMongoRepository]
               ↓
          [MongoDB Atlas]
```

## Pattern Inti
1. **AOP Authorization**: `@RequireAuth` → `AuthorizationAspect` → `AuthorizedChats` (in-memory ConcurrentHashMap)
2. **Reactive Pipeline**: Semua data access via `Mono<T>` / `Flux<T>`, tanpa `.block()`
3. **Command Router**: `CommandHandler` dispatch ke 29+ `CommandProcessor` impl
4. **Callback Router**: `CallbackHandler` dispatch ke 12+ `CallbackProcessor` impl
5. **Event-Driven**: `ApplicationEventPublisher` untuk SLIK events & DB update events
6. **TTL Cache**: `SlikSessionCache` — auto cleanup setiap 5 menit, TTL 30 menit
7. **Webhook Pattern**: WhatsApp → immediate `200 OK` → async processing

## Roles Pengguna
```java
enum AccountOfficerRoles { AO, PIMP, ADMIN }
```
- **AO**: Account Officer — akses command dasar
- **PIMP**: Pimpinan — akses read-only leadership
- **ADMIN**: Administrator — full akses termasuk broadcast & manajemen user

## Technology Dependencies (pom.xml)
| Library | Versi | Fungsi |
|---------|-------|--------|
| TDLight | 3.4.4 | Telegram TDLib native client |
| Playwright | 1.51.0 | Headless Chromium (PDF generation) |
| Apache PDFBox | 2.0.30 | PDF text parsing |
| AWS SDK v2 S3 | latest | Cloudflare R2 object storage |
| OpenCSV | 5.7.1 | CSV import untuk data tagihan |
| Spring AOP | - | @RequireAuth authorization |
| Lombok | - | Boilerplate code generation |

## Build Profiles
- **linux**: TDLight natives untuk production (Linux amd64, OpenSSL 3.x)
- **windows**: TDLight natives untuk development (Windows amd64)

## CI/CD
- GitHub Actions: `.github/workflows/maven.yml`
- Docker support: `Dockerfile` + `docker-compose.yml`

## Monitoring
- Spring Actuator endpoints: `/health`, `/metrics`, `/prometheus`
