---
name: telegram-commands
description: Semua Telegram command handler di project cek-pelunasan — daftar command, handler class, fungsi, dan cara menambah command baru
---

# Telegram Command Handlers

Package: `org.cekpelunasan.platform.telegram.command`

---

## Arsitektur Routing Command

```
TelegramBot.java (UpdateNewMessage)
       ↓
CommandHandler.java (Router / Dispatcher)
       ↓
CommandProcessor (interface) — tiap command implement ini
       ↓
Handler konkret (e.g., SlikCommandHandler)
```

### Interface CommandProcessor
```java
public interface CommandProcessor {
    String getCommand();    // "/slik", "/tab", dll
    void handle(TdApi.Message message);
}
```

### AbstractCommandHandler
Base class untuk semua handler:
```java
public abstract class AbstractCommandHandler implements CommandProcessor {
    protected final TelegramMessageService messageService;
    protected final UserService userService;
    // helper methods: getChatId(), getText(), getArgs()
}
```

### CommandHandler (Router)
```java
// Dispatch berdasarkan prefix command
// "/slik ..." → SlikCommandHandler.handle(message)
```

---

## Daftar Command Handler (29+)

### /slik — SLIK Credit Check
**File**: `command/handler/SlikCommandHandler.java`  
**Role**: AO, PIMP, ADMIN  
**Fungsi**: Cek riwayat kredit nasabah via nomor KTP atau nama

```
/slik <nomor_ktp>    → cek SLIK by KTP
/slik <nama>         → search SLIK by nama (returns list)
```
**Flow**: Command → SlikSessionCache → GeneratePdfFiles → TelegramMessageService (kirim PDF)

---

### /tab — Tabungan (Savings)
**File**: `command/handler/SavingCommandHandler.java`  
**Role**: AO, PIMP, ADMIN  
**Fungsi**: Cek saldo rekening tabungan nasabah

```
/tab <nomor_rekening>  → cek saldo by nomor rekening
/tab <nama>            → search tabungan by nama
```
**Flow**: Command → SavingsService → format → sendMessage

---

### /otor — Otorisasi/Registrasi
**File**: `command/handler/AuthCommandHandler.java`  
**Role**: Semua (pre-auth)  
**Fungsi**: Registrasi user baru dengan kode AO

```
/otor <kode_ao>    → daftarkan chatId dengan kode AO
```
**Flow**: Command → UserService.register() → AuthorizedChats.addChat()

---

### /help — Bantuan
**File**: `command/handler/HelpCommandHandler.java`  
**Role**: Semua  
**Fungsi**: Tampilkan daftar command yang tersedia

---

### /simulasi — Simulasi Pembayaran
**File**: `command/handler/SimulasiCommandHandler.java`  
**Role**: AO, PIMP, ADMIN  
**Fungsi**: Hitung simulasi jadwal pelunasan kredit

```
/simulasi <no_spk>              → simulasi default
/simulasi <no_spk> <extra>      → simulasi dengan pembayaran ekstra
```
**Flow**: Command → SimulasiService.calculate() → format tabel → sendMessage

---

### /broadcast — Kirim Pesan Massal
**File**: `command/handler/BroadcastCommandHandler.java`  
**Role**: ADMIN only  
**Fungsi**: Kirim pesan ke semua user terdaftar

```
/broadcast <pesan>    → broadcast ke semua user aktif
```
**Flow**: Command → UserService.findAll() → loop TelegramMessageService.sendMessage()

---

### /canvasing — Daftar Canvasing
**File**: `command/handler/CanvasingCommandHandler.java`  
**Role**: AO, PIMP, ADMIN  
**Fungsi**: Tampilkan daftar nasabah untuk canvasing/kunjungan

---

### Data Update Commands (ADMIN only)

#### /update-tagihan — Update Data Tagihan
**File**: `command/handler/BillsUpdateDataHandler.java`  
**Fungsi**: Import/refresh data tagihan dari file CSV

#### /update-history — Update Riwayat Kredit
**File**: `command/handler/CreditHistoryUpdateCommandHandler.java`  
**Fungsi**: Sync riwayat credit history

---

### /delete-user — Hapus Akses User
**File**: `command/handler/DeleteUserAccessCommand.java`  
**Role**: ADMIN  
**Fungsi**: Nonaktifkan akses user dari bot

```
/delete-user <chatId>    → nonaktifkan user
```

---

### WhatsApp SLIK Command
**File**: `command/handler/SlikCommandHandler.java` (via WhatsApp)  
**Fungsi**: Expose command `.s <ktp>` di WhatsApp (lihat skill whatsapp-platform)

---

## Cara Menambah Command Baru

### 1. Buat Handler Class
```java
@Component
@RequiredArgsConstructor
public class MyNewCommandHandler extends AbstractCommandHandler {

    @Override
    public String getCommand() {
        return "/mycommand";
    }

    @Override
    @RequireAuth  // tambahkan jika perlu auth
    public void handle(TdApi.Message message) {
        Long chatId = getChatId(message);
        String[] args = getArgs(message);

        if (args.length < 2) {
            messageService.sendMessage(chatId, "Usage: /mycommand <param>");
            return;
        }

        String param = args[1];
        // ... logic ...
        messageService.sendMessage(chatId, "Result: " + result);
    }
}
```

### 2. Spring Auto-Discovery
Spring akan otomatis detect `@Component` dan inject ke `CommandHandler` router via `List<CommandProcessor>`.

### 3. Tambahkan ke /help (opsional)
Edit `HelpCommandHandler.java` untuk menampilkan command baru di daftar bantuan.

---

## Helper Methods di AbstractCommandHandler

```java
// Ambil chatId dari message
protected Long getChatId(TdApi.Message message);

// Ambil full text pesan
protected String getText(TdApi.Message message);

// Split text menjadi args array (split by space)
protected String[] getArgs(TdApi.Message message);

// Cek apakah sender adalah ADMIN
protected boolean isAdmin(Long chatId);

// Cek apakah sender memiliki role tertentu
protected boolean hasRole(Long chatId, AccountOfficerRoles role);
```

---

## @RequireAuth Annotation
```java
@RequireAuth  // taruh di handle() method
public void handle(TdApi.Message message) {
    // hanya dieksekusi jika chatId ada di AuthorizedChats
    // jika tidak, AOP akan kirim pesan "Akses ditolak" otomatis
}
```

**AOP Interceptor**: `AuthorizationAspect` cek `AuthorizedChats.isAuthorized(chatId)` sebelum method dijalankan.
