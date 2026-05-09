---
name: slik-system
description: Sistem SLIK (pengecekan riwayat kredit) di project cek-pelunasan — flow lengkap dari command hingga PDF, session cache, notifikasi, dan cara extend fitur SLIK
---

# SLIK System

SLIK (Sistem Layanan Informasi Keuangan) adalah fitur utama untuk pengecekan riwayat kredit nasabah.

---

## Flow SLIK End-to-End

```
User: /slik 3201234567890001
       ↓
SlikCommandHandler.handle()
       ↓ check SlikSessionCache (sudah pernah search?)
       ↓ jika tidak ada cache:
       ↓ HTTP call ke SLIK endpoint (WebClient)
       ↓ parse response → List<SlikJsonDto>
       ↓ simpan ke SlikSessionCache
       ↓
Jika 1 hasil: langsung generate PDF
Jika banyak hasil: kirim daftar (inline keyboard untuk pilih)
       ↓
User pilih nama (CallbackHandler)
       ↓
GeneratePdfFiles.generateAndUpload()
       ↓ Playwright: buka URL SLIK → screenshot → PDF
       ↓ Upload ke Cloudflare R2
       ↓
Kirim PDF ke Telegram
       ↓
CreditHistoryService.save() — audit log
```

---

## SlikCommandHandler
**File**: `platform/telegram/command/handler/SlikCommandHandler.java`

```java
@Override
public String getCommand() { return "/slik"; }

@Override
@RequireAuth
public void handle(TdApi.Message message) {
    Long chatId = getChatId(message);
    String[] args = getArgs(message);

    if (args.length < 2) {
        messageService.sendMessage(chatId, "Usage: /slik <ktp|nama>");
        return;
    }

    String query = args[1];

    // Cek cache dulu
    String cacheKey = chatId + "_slik_" + query;
    List<SlikJsonDto> cached = slikSessionCache.get(cacheKey);

    if (cached != null) {
        // Pakai hasil cached
        handleResults(chatId, cached, cacheKey);
        return;
    }

    // Fetch dari SLIK endpoint
    webClient.get()
        .uri("/search?q=" + query)
        .retrieve()
        .bodyToFlux(SlikJsonDto.class)
        .collectList()
        .subscribe(results -> {
            slikSessionCache.put(cacheKey, results);
            handleResults(chatId, results, cacheKey);
        });
}

private void handleResults(Long chatId, List<SlikJsonDto> results, String cacheKey) {
    if (results.isEmpty()) {
        messageService.sendMessage(chatId, "❌ Data tidak ditemukan.");
    } else if (results.size() == 1) {
        generateAndSendPdf(chatId, results.get(0));
    } else {
        // Kirim daftar dengan inline keyboard
        sendSelectionList(chatId, results, cacheKey);
    }
}
```

---

## SlikSessionCache
**File**: `core/service/slik/SlikSessionCache.java`

```java
@Service
public class SlikSessionCache {

    private static final long TTL_MS = 30 * 60 * 1000; // 30 menit

    // Map: key → (results, expiryTimestamp)
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void put(String key, List<SlikJsonDto> results) {
        cache.put(key, new CacheEntry(results, System.currentTimeMillis() + TTL_MS));
    }

    public List<SlikJsonDto> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiry) {
            cache.remove(key);
            return null;
        }
        return entry.results;
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    @Scheduled(fixedDelay = 300_000) // tiap 5 menit
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now > entry.getValue().expiry);
    }
}
```

---

## SlikJsonDto
**File**: `core/service/slik/dto/SlikJsonDto.java`

```java
class SlikJsonDto {
    String id;           // ID unik dari SLIK endpoint
    String name;         // Nama nasabah (raw dari SLIK)
    String ktpNumber;    // Nomor KTP
    String dateOfBirth;  // Tanggal lahir
    String address;      // Alamat
    String pdfUrl;       // URL untuk generate PDF
}
```

---

## GeneratePdfFiles
**File**: `core/service/slik/GeneratePdfFiles.java`

```java
@Service
@RequiredArgsConstructor
public class GeneratePdfFiles {

    private final PlaywrightBrowserPool browserPool;
    private final S3Client s3Client;

    // Generate PDF dari URL SLIK dan upload ke R2
    public Mono<String> generateAndUpload(SlikJsonDto dto) {
        return Mono.fromCallable(() -> {
            BrowserContext context = browserPool.acquire();
            try {
                Page page = context.newPage();
                page.navigate(dto.getPdfUrl());
                page.waitForLoadState(LoadState.NETWORKIDLE);

                // Generate PDF
                byte[] pdfBytes = page.pdf(
                    new Page.PdfOptions()
                        .setFormat("A4")
                        .setPrintBackground(true)
                );

                // Upload ke Cloudflare R2
                String fileName = "slik/" + dto.getKtpNumber() + "_"
                    + System.currentTimeMillis() + ".pdf";

                s3Client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .contentType("application/pdf")
                        .build(),
                    RequestBody.fromBytes(pdfBytes)
                );

                return "https://cdn.example.com/" + fileName;

            } finally {
                browserPool.release(context);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

---

## PDFReader
**File**: `core/service/slik/PDFReader.java`

```java
@Service
public class PDFReader {

    // Extract semua teks dari PDF bytes
    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    // Extract teks per halaman
    public List<String> extractByPage(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(stripper.getText(doc));
            }
            return pages;
        }
    }
}
```

---

## SlikNameFormatter
**File**: `core/service/slik/SlikNameFormatter.java`

```java
@Component
public class SlikNameFormatter {

    // Format ke Title Case: "BUDI SANTOSO" → "Budi Santoso"
    public String format(String rawName) {
        if (rawName == null) return "";
        return Arrays.stream(rawName.trim().split("\\s+"))
            .map(word -> word.substring(0, 1).toUpperCase()
                + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    // Sanitize nama dari karakter khusus
    public String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z\\s]", "").trim();
    }

    // Split nama untuk display
    public String shortName(String fullName, int maxLength) {
        String formatted = format(fullName);
        if (formatted.length() <= maxLength) return formatted;
        return formatted.substring(0, maxLength - 3) + "...";
    }
}
```

---

## SendNotificationSlikUpdated
**File**: `core/service/slik/SendNotificationSlikUpdated.java`

```java
@Service
public class SendNotificationSlikUpdated {

    // Scheduled: cek file SLIK baru setiap X menit
    @Scheduled(fixedDelayString = "${slik.notification.interval:600000}")
    public void checkAndNotify() {
        // 1. Ambil daftar file terbaru dari R2
        // 2. Cek mana yang belum ada di SlikNotifiedFileRepository
        // 3. Kirim notifikasi ke semua user ADMIN
        // 4. Simpan ke SlikNotifiedFileRepository

        s3Client.listObjects(...)
            .contents()
            .stream()
            .filter(obj -> !isAlreadyNotified(obj.key()))
            .forEach(obj -> notifyAdmins(obj.key()));
    }

    private void notifyAdmins(String fileName) {
        userRepository.findByRole(AccountOfficerRoles.ADMIN)
            .subscribe(admin ->
                messageService.sendMessage(
                    admin.getChatId(),
                    "📄 File SLIK baru tersedia: " + fileName
                )
            );
        // Simpan ke DB agar tidak kirim ulang
        slikNotifiedFileRepository.save(
            new SlikNotifiedFile(fileName, LocalDateTime.now())
        ).subscribe();
    }
}
```

---

## SlikEvent System
**File**: `core/event/SlikEvent.java`

```java
// Event dipublish setelah SLIK check berhasil
public class SlikEvent extends ApplicationEvent {
    private final Long chatId;
    private final String ktpNumber;
    private final String result;
}

// Publish event
applicationEventPublisher.publishEvent(
    new SlikEvent(this, chatId, ktpNumber, "success")
);

// Listen event (untuk audit log)
@EventListener
public void onSlikEvent(SlikEvent event) {
    creditHistoryService.save(
        event.getChatId(),
        event.getKtpNumber(),
        event.getName(),
        event.getResult()
    ).subscribe();
}
```

---

## WhatsApp SLIK (HandlerSlik)
**File**: `platform/whatsapp/service/HandlerSlik.java`

Sama dengan Telegram tapi output ke WhatsApp:
```
.s 3201234567890001
→ cek SLIK by KTP
→ kirim PDF via WhatsApp document
```

---

## Extend Fitur SLIK

### Tambah field baru ke SlikJsonDto
```java
// Tambah field di SlikJsonDto.java
String creditScore;  // field baru dari SLIK API

// Pakai di display
String message = String.format(
    "Nama: %s\nKTP: %s\nCredit Score: %s",
    dto.getName(), dto.getKtpNumber(), dto.getCreditScore()
);
```

### Custom PDF Template
```java
// Di GeneratePdfFiles — inject custom CSS sebelum generate
page.addStyleTag(new Page.AddStyleTagOptions()
    .setContent(".header { color: #003366; }"));
byte[] pdf = page.pdf(...);
```
