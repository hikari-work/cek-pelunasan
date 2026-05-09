---
name: service-layer
description: Semua service class di project cek-pelunasan — tanggung jawab, method utama, dan cara menggunakannya
---

# Service Layer

Package: `org.cekpelunasan.core.service`

---

## AuthorizedChats
**File**: `core/service/auth/AuthorizedChats.java`

**Fungsi**: In-memory whitelist chatId yang boleh menggunakan bot. Digunakan oleh `AuthorizationAspect`.

```java
// Load semua authorized chatId dari DB ke memory saat startup
void loadAuthorizedChats();

// Cek apakah chatId boleh akses
boolean isAuthorized(Long chatId);

// Tambah chatId baru (setelah user register)
void addChat(Long chatId);

// Hapus chatId (setelah user dinonaktifkan)
void removeChat(Long chatId);

// Cek role user
boolean hasRole(Long chatId, AccountOfficerRoles role);
```

**Implementasi**: `ConcurrentHashMap<Long, User>` — thread-safe, O(1) lookup.

---

## BillService
**File**: `core/service/bill/BillService.java`

**Fungsi**: CRUD tagihan kredit, import CSV, query per AO/cabang.

```java
// Import data tagihan dari file CSV ke MongoDB
Mono<Void> importFromCsv(MultipartFile file);

// Cari tagihan by SPK number
Mono<Bills> findByNoSpk(String noSpk);

// Cari tagihan per AO dengan pagination
Flux<Bills> findByAo(String ao, Boolean payDown, int page);

// Cari tagihan per cabang
Flux<Bills> findByBranch(String branch, Boolean payDown);

// Fuzzy search by nama per cabang
Flux<Bills> searchByName(String name, String branch);

// Hitung total tagihan per AO
Mono<Double> totalByAo(String ao);

// Hitung total tagihan per cabang
Mono<Double> totalByBranch(String branch);

// Update status bayar
Mono<Bills> markAsPaid(String noSpk, Long chatId);
```

---

## HotKolekService
**File**: `core/service/bill/HotKolekService.java`

**Fungsi**: Quick lookup tagihan dari nomor pendek (12 digit identifikasi).

```java
// Lookup cepat dari kode 12 digit
Mono<Bills> findByShortCode(String code);
```

---

## CreditHistoryService
**File**: `core/service/credithistory/CreditHistoryService.java`

**Fungsi**: Audit log pengecekan SLIK.

```java
// Simpan riwayat pengecekan SLIK
Mono<CreditHistory> save(Long chatId, String ktpNumber, String name, String result);

// Ambil riwayat per chatId
Flux<CreditHistory> findByChatId(Long chatId);

// Ambil riwayat per KTP
Flux<CreditHistory> findByKtp(String ktpNumber);
```

---

## KolekTasService
**File**: `core/service/kolektas/KolekTasService.java`

**Fungsi**: Manajemen daftar kunjungan koleksi nasabah bermasalah.

```java
// Daftar kolek per AO
Flux<KolekTas> findByAo(String ao);

// Daftar kolek per cabang
Flux<KolekTas> findByBranch(String branch);

// Tandai sudah dikunjungi
Mono<KolekTas> markVisited(String noSpk, String notes);

// Tambah ke daftar kolek
Mono<KolekTas> addToList(KolekTas kolekTas);
```

---

## SavingsService
**File**: `core/service/savings/SavingsService.java`

**Fungsi**: Query rekening tabungan nasabah.

```java
// Cari by nomor rekening
Mono<Savings> findByTabId(String tabId);

// Fuzzy search by nama
Flux<Savings> searchByName(String name);

// Rekening per cabang
Flux<Savings> findByBranch(String branch);
```

---

## SimulasiService
**File**: `core/service/simulasi/SimulasiService.java`

**Fungsi**: Kalkulasi dan simpan simulasi jadwal pembayaran pelunasan.

```java
// Hitung simulasi pelunasan untuk SPK tertentu
Mono<SimulasiResult> calculate(String noSpk, Double extraPayment);

// Ambil baris simulasi per SPK
Flux<Simulasi> getSchedule(String noSpk);

// Hapus simulasi lama per SPK
Mono<Void> deleteByNoSpk(String noSpk);
```

---

## UserService
**File**: `core/service/users/UserService.java`

**Fungsi**: Registrasi dan manajemen user bot.

```java
// Cari user by chatId
Mono<User> findById(Long chatId);

// Cari user by kode AO
Mono<User> findByCode(String userCode);

// Register user baru
Mono<User> register(Long chatId, String userCode, AccountOfficerRoles role);

// Update preferensi cabang
Mono<User> updateBranch(Long chatId, String branchCode);

// Nonaktifkan user
Mono<User> deactivate(Long chatId);

// Set role user
Mono<User> setRole(Long chatId, AccountOfficerRoles role);
```

---

## DataUpdateLogService
**File**: `core/service/log/DataUpdateLogService.java`

**Fungsi**: Track waktu terakhir data diupdate per tipe.

```java
// Ambil log update per tipe data
Mono<DataUpdateLog> getLog(String dataType);

// Update timestamp setelah import data
Mono<DataUpdateLog> markUpdated(String dataType, String updatedBy);
```

---

## PaymentDetailsService
**File**: `core/service/paymentdetails/PaymentDetailsService.java`

**Fungsi**: Detail komponen pembayaran (pokok/bunga/denda).

```java
// Detail per paymentId
Flux<PaymentDetails> findByPaymentId(String paymentId);

// Simpan detail payment
Mono<PaymentDetails> save(PaymentDetails details);
```

---

## SLIK Services

### GeneratePdfFiles
**File**: `core/service/slik/GeneratePdfFiles.java`

**Fungsi**: Generate PDF laporan SLIK menggunakan Playwright headless Chromium.

```java
// Generate PDF dari URL SLIK endpoint
Mono<byte[]> generatePdf(String url, String sessionToken);

// Generate dan upload ke R2, return URL
Mono<String> generateAndUpload(String ktpNumber, String sessionToken);
```

### PDFReader
**File**: `core/service/slik/PDFReader.java`

**Fungsi**: Parse teks dari file PDF menggunakan Apache PDFBox.

```java
// Extract semua teks dari PDF bytes
String extractText(byte[] pdfBytes);

// Extract per halaman
List<String> extractByPage(byte[] pdfBytes);
```

### SlikSessionCache
**File**: `core/service/slik/SlikSessionCache.java`

**Fungsi**: In-memory cache untuk hasil pencarian SLIK dengan TTL 30 menit.

```java
// Simpan hasil pencarian
void put(String sessionKey, List<SlikJsonDto> results);

// Ambil hasil (null jika expired/tidak ada)
List<SlikJsonDto> get(String sessionKey);

// Hapus manual
void invalidate(String sessionKey);

// Auto cleanup scheduled setiap 5 menit
@Scheduled(fixedDelay = 300_000)
void cleanExpired();
```

### SlikNameFormatter
**File**: `core/service/slik/SlikNameFormatter.java`

**Fungsi**: Normalisasi dan parsing nama nasabah dari data SLIK.

```java
// Format nama ke Title Case
String format(String rawName);

// Parse first/last name dari nama lengkap
String[] parseName(String fullName);

// Bersihkan karakter khusus
String sanitize(String name);
```

### SendNotificationSlikUpdated
**File**: `core/service/slik/SendNotificationSlikUpdated.java`

**Fungsi**: Kirim notifikasi ke user ketika file SLIK baru tersedia.

```java
// Cek file SLIK baru dan kirim notifikasi ke semua ADMIN
Mono<Void> checkAndNotify();

// Kirim notifikasi ke chatId tertentu
Mono<Void> notifyUser(Long chatId, String fileName);
```

---

## TelegramMessageService
**File**: `platform/telegram/service/TelegramMessageService.java`

**Fungsi**: Helper untuk mengirim pesan/media ke Telegram via TDLight.

```java
// Kirim text message
void sendMessage(Long chatId, String text);

// Kirim text dengan parse mode (Markdown/HTML)
void sendMessage(Long chatId, String text, String parseMode);

// Kirim text dengan inline keyboard
void sendMessage(Long chatId, String text, TdApi.ReplyMarkupInlineKeyboard keyboard);

// Edit pesan yang sudah ada
void editMessage(Long chatId, Long messageId, String newText);

// Kirim dokumen/file
void sendDocument(Long chatId, byte[] data, String fileName, String caption);

// Kirim foto
void sendPhoto(Long chatId, byte[] imageData, String caption);

// Delete pesan
void deleteMessage(Long chatId, Long messageId);
```

---

## Cara Inject Service
```java
@Service
@RequiredArgsConstructor
public class SomeCommandHandler implements CommandProcessor {
    private final BillService billService;
    private final UserService userService;
    private final TelegramMessageService messageService;

    @Override
    @RequireAuth
    public void handle(TdApi.Message message) {
        Long chatId = message.chatId;
        // gunakan service...
    }
}
```
