Planning: Migrasi WhatsApp Webhook ke Format Baru + WebFlux/WebClient

---

## Latar Belakang

Gateway WhatsApp (GOWA) diperbarui ke versi baru dengan struktur payload yang berbeda secara fundamental.
Bersamaan dengan itu, stack HTTP inbound/outbound dimigrasikan ke WebFlux + WebClient.

---

## Perbedaan Payload Lama vs Baru

```
LAMA (flat top-level):
{
  "sender_id": "628xxx@s.whatsapp.net",
  "chat_id": "628xxx@s.whatsapp.net",
  "from": "628xxx@s.whatsapp.net",
  "event": "message",
  "action": null,
  "message": { "text": "...", "id": "...", "replied_id": null },
  "image": { "url": "...", "mimetype": "..." }
}

BARU (nested payload):
{
  "event": "message",
  "device_id": "628xxx@s.whatsapp.net",
  "timestamp": "2026-01-01T10:00:00Z",
  "payload": {
    "id": "3EB0C127...",
    "chat_id": "628xxx@s.whatsapp.net",
    "from": "628xxx@s.whatsapp.net",
    "from_name": "John Doe",
    "is_from_me": false,
    "body": "Hello",
    "replied_to_id": null,
    "quoted_body": null
  }
}
```

### Mapping Field Kritis

| Lama | Baru |
|------|------|
| `webhook.getSenderId()` | `webhook.getPayload().getFrom()` |
| `webhook.getChatId()` | `webhook.getPayload().getChatId()` |
| `webhook.getPushname()` | `webhook.getPayload().getFromName()` |
| `webhook.getMessage().getText()` | `webhook.getPayload().getBody()` |
| `webhook.getMessage().getId()` | `webhook.getPayload().getId()` |
| `webhook.getMessage().getRepliedId()` | `webhook.getPayload().getRepliedToId()` |
| `webhook.getFromLid()` | `webhook.getPayload().getFromLid()` |
| `webhook.getAction().equals("message_revoked")` | `"message.revoked".equals(webhook.getEvent())` |
| `webhook.getAction().equals("message_edited")` | `"message.edited".equals(webhook.getEvent())` |
| `webhook.isGroupEvent()` → `group.participants` | `"group.participants".equals(webhook.getEvent())` |
| `webhook.isReceiptEvent()` → `message.ack` | `"message.ack".equals(webhook.getEvent())` |
| `ReceiptPayloadDTO payload` (nested) | `MessagePayloadDTO payload` dengan field receipt di dalamnya |
| `image/video/audio/document` (MediaDTO) | `payload.image/video/audio/document` (MediaPayloadDTO, bisa String atau Object) |

---

## Perubahan Stack (WebFlux)

| Komponen | Lama | Baru |
|----------|------|------|
| Controller return type | `ResponseEntity<?>` | `Mono<ResponseEntity<?>>` |
| Async processing | `CompletableFuture.runAsync()` | `Mono.fromRunnable().subscribeOn(Schedulers.boundedElastic())` |
| Outbound HTTP | `RestTemplate` di `WhatsAppSender` | `WebClient` |
| Config | Tidak ada | `WebClientConfiguration` bean |

> **Catatan:** Stack tetap Servlet/Tomcat (spring-boot-starter-web dipertahankan). `spring-boot-starter-webflux`
> ditambahkan **hanya untuk WebClient**. Spring MVC mendukung return type `Mono<>` secara native.
> Tidak perlu migrasi JPA ke R2DBC — blocking call dibungkus `Schedulers.boundedElastic()`.

---

## Urutan Pengerjaan (5 Phase)

### Phase 1 — pom.xml (1 file)
- Tambah `spring-boot-starter-webflux` (untuk WebClient)
- Hapus `native-maven-plugin` (tidak relevan, sudah ada sejak lama)
- Tidak menghapus `spring-boot-starter-web`

### Phase 2 — DTO Baru (4 file baru + 1 file dihapus)

**Buat baru:**
- `dto/webhook/MessagePayloadDTO.java` — unified payload untuk semua event type:
  - `id`, `chatId`, `from`, `fromLid`, `fromName`, `timestamp`, `isFromMe`
  - `body` (teks pesan)
  - `repliedToId`, `quotedBody` (reply)
  - `reaction`, `reactedMessageId` (reaksi)
  - `image`, `video`, `audio`, `document`, `sticker` (type: `MediaPayloadDTO`)
  - `ids` (List), `receiptType`, `receiptTypeDescription` (untuk `message.ack`)
  - `type`, `jids` (List) (untuk `group.participants`)
  - `state`, `media`, `isGroup` (untuk `chat_presence`)
  - `callId`, `autoRejected`, `remotePlatform` (untuk `call.offer`)
  - Helper: `isGroupChat()`, `getCleanFrom()`, `getCleanChatId()`
- `dto/webhook/MediaPayloadDTO.java` — flexible media (path/url + caption):
  - `path` (String) — saat auto-download enabled
  - `url` (String) — saat auto-download disabled
  - `caption` (String)
  - Custom deserializer `MediaPayloadDeserializer` untuk handle kasus `image` berupa String polos
- `dto/webhook/MediaPayloadDeserializer.java` — Jackson custom deserializer:
  - Jika node berupa `TextNode` → set hanya `path`
  - Jika node berupa `ObjectNode` → parse `path`, `url`, `caption`

**Tulis ulang:**
- `dto/webhook/WhatsAppWebhookDTO.java` — struktur baru:
  - Hapus semua field lama (senderId, message, image, video, dll.)
  - Field baru: `event`, `deviceId`, `timestamp`, `payload` (MessagePayloadDTO)
  - Pertahankan helper methods: `isTextMessage()`, `isGroupChat()`, `isReceiptEvent()`, dll. — tapi delegate ke `payload`
  - Tambah helper: `isMessageRevoked()` cek `"message.revoked".equals(event)`, `isMessageEdited()` cek `"message.edited".equals(event)`

**Hapus:**
- `dto/webhook/MessageDTO.java` — diganti `MessagePayloadDTO.payload.body + id + repliedToId`
- `dto/webhook/MediaDTO.java` — diganti `MediaPayloadDTO`
- `dto/webhook/ReceiptPayloadDTO.java` — digabung ke `MessagePayloadDTO`
- `dto/webhook/RegularMessageDTO.java` — tidak relevan lagi
- `dto/webhook/WebhookBaseDTO.java` — tidak relevan lagi
- `dto/webhook/ContextInfoDTO.java` — tidak relevan lagi
- `dto/webhook/DisappearingModeDTO.java` — tidak relevan lagi
- `dto/webhook/MessageEditedDTO.java` — sekarang cukup dari event type
- `dto/webhook/MessageRevokedDTO.java` — sekarang cukup dari event type
- `dto/webhook/ReceiptEventDTO.java` — digabung ke `MessagePayloadDTO`

**Pertahankan (masih relevan):**
- `dto/webhook/ReactionDTO.java` → review, mungkin bisa digabung ke payload
- `dto/webhook/LocationDTO.java` → pertahankan, referensikan dari `MessagePayloadDTO`
- `dto/webhook/ContactDTO.java` → pertahankan
- `dto/webhook/GroupEventDTO.java`, `GroupEventPayloadDTO.java` → review, mungkin hapus jika sudah di `MessagePayloadDTO`

### Phase 3 — WebClient + Controller (3 file)

**Buat baru:**
- `configuration/WebClientConfiguration.java`:
  ```java
  @Bean
  public WebClient whatsappWebClient(
      @Value("${whatsapp.gateway.url}") String baseUrl,
      @Value("${whatsapp.gateway.username}") String username,
      @Value("${whatsapp.gateway.password}") String password
  ) {
      return WebClient.builder()
          .baseUrl(baseUrl)
          .defaultHeaders(h -> h.setBasicAuth(username, password))
          .build();
  }
  ```

**Tulis ulang:**
- `platform/whatsapp/service/sender/WhatsAppSender.java`:
  - Hapus `RestTemplate`, inject `WebClient`
  - Method `request()` return `Mono<GenericResponseDTO>`
  - Gunakan `webClient.post().uri(path).bodyValue(dto).retrieve().bodyToMono(GenericResponseDTO.class)`
  - Untuk URL dengan `{message_id}` gunakan `.uri(uri -> uri.path(...).build(messageId))`

- `controller/WebhookController.java`:
  - Return type: `Mono<ResponseEntity<?>>`
  - Process async via `Mono.fromRunnable(() -> routers.handle(dto)).subscribeOn(Schedulers.boundedElastic())`
  - Return `Mono.just(ResponseEntity.ok("OK"))` segera tanpa menunggu proses selesai

### Phase 4 — Routers + Services (9 file)

Semua file ini mengakses field dari `WhatsAppWebhookDTO`. Update mapping field sesuai tabel di atas.

- `service/Routers.java`:
  - `webhook.getMessage().getText()` → `webhook.getPayload().getBody()`
  - `webhook.isGroupChat()` → `webhook.getPayload().isGroupChat()`
  - `isValidTextMessage()` → cek `"message".equals(webhook.getEvent()) && payload.getBody() != null`
  - Log: `webhook.getCleanChatId()` → `webhook.getPayload().getCleanChatId()`

- `service/hotkolek/HandleKolekCommand.java` — update field access
- `service/jatuhbayar/JatuhBayarService.java` — update field access
- `service/pelunasan/HandlerPelunasan.java` — update field access
- `service/pelunasan/PelunasanService.java` — update field access
- `service/shortcut/ShortcutMessages.java` — update field access
- `service/slik/SlikService.java` — update field access
- `service/tabungan/TabunganService.java` — update field access
- `service/virtualaccount/VirtualAccountHandler.java` — update field access

### Phase 5 — WhatsAppSenderService + Build Verification (2 file)

- `service/sender/WhatsAppSenderService.java`:
  - Semua method return type dari `GenericResponseDTO` → `Mono<GenericResponseDTO>`
  - `.block()` hanya jika diperlukan sinkron (hindari jika bisa)
  - Atau tetap sync dengan `.block()` karena dipanggil dari blocking context (`@Async`)

- `service/utils/HotKolekMessageGenerator.java` — review apakah ada akses ke DTO lama

Terakhir: `mvn clean package` untuk verifikasi tidak ada error kompilasi.

---

## File Summary

| Status | File | Phase |
|--------|------|-------|
| BARU | `dto/webhook/MessagePayloadDTO.java` | 2 |
| BARU | `dto/webhook/MediaPayloadDTO.java` | 2 |
| BARU | `dto/webhook/MediaPayloadDeserializer.java` | 2 |
| BARU | `configuration/WebClientConfiguration.java` | 3 |
| TULIS ULANG | `dto/webhook/WhatsAppWebhookDTO.java` | 2 |
| TULIS ULANG | `platform/whatsapp/service/sender/WhatsAppSender.java` | 3 |
| TULIS ULANG | `controller/WebhookController.java` | 3 |
| TULIS ULANG | `service/Routers.java` | 4 |
| UPDATE | 8 service files | 4 |
| UPDATE | `service/sender/WhatsAppSenderService.java` | 5 |
| HAPUS | 8 DTO files lama | 2 |
| UPDATE | `pom.xml` | 1 |

---

## Risiko & Catatan Penting

1. **`image` bisa String atau Object** — perlu custom Jackson deserializer `MediaPayloadDeserializer`.
   Jika tidak, Jackson akan error saat field `image` berupa plain string tapi type-nya object.

2. **`RestTemplate` → `WebClient` bersifat async** — `WhatsAppSenderService` saat ini dipanggil dari `@Async` method.
   Bisa tetap `.block()` di `WhatsAppSenderService` untuk menjaga sinkronisitas tanpa refactor besar,
   tapi idealnya return `Mono` dan subscribe di caller.

3. **Tidak ada breaking change di response** — gateway hanya berubah di arah inbound (webhook).
   Format kirim pesan (outbound: `/send/message`, dll.) perlu dikonfirmasi apakah ikut berubah atau tidak.

4. **`message.revoked` dan `message.edited` sekarang event tersendiri** — bukan lagi field `action`.
   Pastikan `Routers` menangani event ini secara eksplisit jika diperlukan.

5. **`device_id` menggantikan `sender_id`** — `device_id` adalah ID perangkat bot sendiri (bukan pengirim).
   Pengirim pesan ada di `payload.from`.

---

---

# Bagian 2: MongoDB Reactive + S3Async + Playwright PDF

---

## Phase 6 — MySQL/JPA → MongoDB Reactive

### Motivasi

Migrasi dari MySQL + Spring Data JPA ke MongoDB + Spring Data MongoDB Reactive agar seluruh data layer menjadi non-blocking dan kompatibel dengan stack WebFlux yang sudah dibangun di Phase 1–5.

### pom.xml

Hapus:
- `spring-boot-starter-data-jpa`
- `mysql-connector-java` (atau driver MySQL yang dipakai)

Tambah:
- `spring-boot-starter-data-mongodb-reactive`

Env var baru:
```
MONGODB_URI=mongodb://host:27017/cek_pelunasan
```

Hapus: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `MAX_POOL`

### Konversi Entities (11 file)

Perubahan per file:

| File | Perubahan Utama |
|------|----------------|
| `Bills.java` | `@Entity @Table(name="tagihan")` → `@Document(collection="tagihan")` — `noSpk` tetap `@Id String` |
| `User.java` | `@Entity(name="users")` → `@Document(collection="users")` — `chatId` tetap `@Id Long` |
| `CreditHistory.java` | `@Entity` → `@Document` — review apakah punya relasi JPA |
| `CustomerHistory.java` | `@Entity` → `@Document` |
| `KolekTas.java` | `@Entity` → `@Document` |
| `Logging.java` | `@Entity` → `@Document` |
| `Paying.java` | `@Entity` → `@Document` |
| `Payment.java` | `@Entity` → `@Document` |
| `Savings.java` | `@Entity` → `@Document` |
| `Simulasi.java` | `@Entity` → `@Document` |
| `SimulasiResult.java` | `@Entity` → `@Document` |

Annotations yang dihapus dari semua entity:
- `jakarta.persistence.*` → diganti `org.springframework.data.annotation.Id` dan `org.springframework.data.mongodb.core.mapping.Document`
- `@Table`, `@Column`, `@Index`, `@Enumerated`, `@OneToMany`, `@ManyToOne`, `@JoinColumn`

Annotations yang dipertahankan atau diganti:
- `@Id` → tetap, tapi import dari `org.springframework.data.annotation.Id`
- `@Enumerated(EnumType.STRING)` → MongoDB menyimpan enum sebagai String secara default, hapus annotation ini

### Konversi Repositories (9 file)

Semua `extends JpaRepository<T, ID>` → `extends ReactiveMongoRepository<T, ID>`

Return type perubahan:

| Tipe lama | Tipe baru |
|-----------|-----------|
| `T` | `Mono<T>` |
| `Optional<T>` | `Mono<T>` |
| `List<T>` | `Flux<T>` |
| `Set<T>` | `Flux<T>` |
| `Page<T>` | `Flux<T>` (konten page, pisahkan count query) |
| `void` | `Mono<Void>` |

**Khusus `BillsRepository`:**

- `deleteAllFast()` → `deleteAll()` return `Mono<Void>` — TRUNCATE tidak ada di MongoDB, `deleteAll()` cukup efisien
- `findUnpaidBillsByBranch(branch)` — query NOT IN subquery:
  Tidak bisa langsung. Strategi: panggil `payingRepository.findAll()` → collect semua ID → pakai `findByBranchAndNoSpkNotIn(branch, ids)` atau gunakan `ReactiveMongoTemplate` + aggregation
- Semua method `Page<Bills> findBy...(..., Pageable pageable)` → `Flux<Bills> findBy...(..., Pageable pageable)` + tambahkan `Mono<Long> countBy...(...)` untuk total count
- Query JPQL `@Query("SELECT b FROM Bills b WHERE ...")` → diganti `@Query("{ ... }")` MongoDB query JSON
- `findDistinctBranchByBranch()` → `distinct("branch", Bills.class)` via `ReactiveMongoTemplate`
- `findDistinctByAccountOfficer()` → sama

**Contoh konversi query:**
```java
// LAMA (JPQL)
@Query("SELECT b FROM Bills b WHERE b.branch = :branch AND b.dueDate LIKE CONCAT(:dueDate, '%')")
List<Bills> findByBranchAndDueDateContaining(String branch, String dueDate);

// BARU (MongoDB)
Flux<Bills> findByBranchAndDueDateStartingWith(String branch, String dueDate);
// ATAU dengan @Query MongoDB:
@Query("{ 'branch': ?0, 'dueDate': { $regex: '^?1' } }")
Flux<Bills> findByBranchAndDueDateContaining(String branch, String dueDate);
```

### Konversi Service Layer

Semua service yang inject repository perlu menyesuaikan return type dan cara konsumsi:

```java
// LAMA
List<Bills> bills = billsRepository.findAllByBranch(branch);
bills.forEach(b -> ...);

// BARU
billsRepository.findAllByBranch(branch)
    .doOnNext(b -> ...)
    .then()
    .subscribe();

// ATAU jika dipanggil dari blocking context (@Async / virtual thread):
List<Bills> bills = billsRepository.findAllByBranch(branch)
    .collectList()
    .block();
```

**File service yang terdampak (update call site):**
- `service/Bill/` — import CSV, batch insert → `saveAll(Flux<Bills>)` atau `save()` per item
- `service/simulasi/` — query Simulasi
- `service/auth/AuthorizedChats` — query User (pre-load ke ConcurrentHashMap)
- `core/event/DatabaseUpdateListener.java`
- `core/lifecycle/PreRun.java`
- Semua 27 command handlers yang memanggil repository via service

### Hapus

- `configuration/AsyncConfiguration.java` jika hanya mengonfigurasi executor JPA/datasource
- Dependency Hikari connection pool (otomatis hilang saat JPA dihapus)

---

## Phase 7 — S3Client → S3AsyncClient

### Motivasi

`S3Client` (sync blocking) diganti `S3AsyncClient` yang return `CompletableFuture` → wrap ke `Mono.fromFuture()` agar non-blocking.

### S3ClientConfiguration.java

```java
// HAPUS
@Bean public S3Client s3Client() { ... }
public byte[] getFile(String key) { ... }           // blocking
public List<String> listObjectFoundByName(...) { ... } // blocking loop

// GANTI DENGAN
@Bean
public S3AsyncClient s3AsyncClient() {
    return S3AsyncClient.builder()
        .endpointOverride(URI.create(endpointUrl))
        .credentialsProvider(createCredentialsProvider())
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .region(Region.US_EAST_1)
        .build();
}

public Mono<byte[]> getFile(String key) {
    return Mono.fromFuture(s3AsyncClient().getObject(
        GetObjectRequest.builder().bucket(bucket).key(key).build(),
        AsyncResponseTransformer.toBytes()
    )).map(ResponseBytes::asByteArray);
}

public Flux<String> listObjectFoundByName(String prefix) {
    return Mono.fromFuture(s3AsyncClient().listObjectsV2(
        ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()
    ))
    .flatMapMany(response -> Flux.fromIterable(response.contents()))
    .map(S3Object::key);
    // Note: paginasi multi-halaman perlu expand() atau recursive Mono chain
}
```

### File yang Diupdate

| File | Perubahan |
|------|-----------|
| `S3ClientConfiguration.java` | Ganti bean `S3Client` → `S3AsyncClient`, update `getFile()` dan `listObjectFoundByName()` |
| `SendNotificationSlikUpdated.java` | Inject `S3AsyncClient`, `listObjectsV2()` → `Mono.fromFuture()`, `@Scheduled` method menjadi `Mono.fromFuture(...).subscribe(...)` |
| `GenerateMetadataSlikForUncompletedDocument.java` | `headObject()` → `Mono.fromFuture()`, `copyObject()` → `Mono.fromFuture()`, chain dengan `.flatMap()` |
| `SlikService.java` | `listObjectsV2()` → `Mono.fromFuture()`, method `handleSlikService()` return `Mono<Void>` |

### pom.xml

Dependency `software.amazon.awssdk:s3` sudah ada. Tambah:
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3-transfer-manager</artifactId> <!-- opsional, untuk upload/download besar -->
</dependency>
```

`S3AsyncClient` sudah ada di `software.amazon.awssdk:s3` yang sama, tidak perlu dependency baru.

---

## Phase 8 — GeneratePdfFiles: OkHttp + iText → WebClient + Playwright

### Motivasi

- **OkHttp** → **WebClient**: API call ke `generate.php` menggunakan WebClient multipart (sudah ada dari Phase 3)
- **iText + Flying Saucer** → **Playwright**: rendering PDF via Chromium headless, lebih akurat CSS rendering dibanding iText
- Seluruh chain `fetchHtml → manipulateHtml → generatePdf` menjadi `Mono` chain

### pom.xml

Hapus:
```xml
<dependency>com.itextpdf:html2pdf</dependency>
<dependency>org.xhtmlrenderer:flying-saucer-core</dependency>
<!-- OkHttp dipertahankan HANYA jika masih dipakai GeneratePdfFiles — hapus jika sudah full WebClient -->
```

Tambah:
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.51.0</version> <!-- atau versi terbaru -->
</dependency>
```

> **Catatan Playwright di Docker:** Perlu base image yang punya Chromium dependencies atau install via `playwright install chromium`. Tambahkan ke Dockerfile:
> ```dockerfile
> RUN mvn dependency:resolve && \
>     java -cp target/dependency/* com.microsoft.playwright.CLI install chromium
> ```

### GeneratePdfFiles.java — Tulis Ulang Total

**Lama (blocking chain):**
```
byte[] pdfBytes
  → OkHttp POST → String html (blocking)
  → Jsoup parse + manipulate → Document (CPU blocking)
  → iText HtmlConverter → byte[] pdf (CPU blocking)
```

**Baru (reactive chain):**
```
Mono<byte[]> pdfBytesMono
  → WebClient multipart POST → Mono<String> html
  → flatMap: Mono.fromCallable(Jsoup.parse + manipulate).subscribeOn(boundedElastic)
  → flatMap: Mono.fromCallable(Playwright render PDF).subscribeOn(boundedElastic)
  → Mono<byte[]>
```

**Method signatures baru:**
```java
// LAMA
public String generateHtmlContent(byte[] pdfBytes, boolean fasilitasAktif)
public Document parsingHtmlContentAndManipulatePages(String htmlContent)
public byte[] generatePdfBytes(Document htmlContent)

// BARU
public Mono<String> fetchHtmlFromEndpoint(byte[] pdfBytes, boolean fasilitasAktif)
public Mono<Document> parseAndManipulateHtml(String htmlContent)
public Mono<byte[]> renderPdfWithPlaywright(String htmlContent)

// Entry point tunggal:
public Mono<byte[]> generatePdf(byte[] pdfBytes, boolean fasilitasAktif) {
    return fetchHtmlFromEndpoint(pdfBytes, fasilitasAktif)
        .flatMap(this::parseAndManipulateHtml)
        .map(Document::outerHtml)
        .flatMap(this::renderPdfWithPlaywright);
}
```

**`fetchHtmlFromEndpoint` dengan WebClient:**
```java
private Mono<String> fetchHtmlFromEndpoint(byte[] body, boolean fasilitasAktif) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("fileToUpload", body)
           .filename("ideb.txt")
           .contentType(MediaType.TEXT_PLAIN);
    builder.part("fasilitasAktif", fasilitasAktif ? "y" : "n");

    return webClient.post()
        .uri(pdfEndpointUrl)
        .header("User-Agent", USER_AGENT)
        .body(BodyInserters.fromMultipartData(builder.build()))
        .retrieve()
        .bodyToMono(String.class);
}
```

**`renderPdfWithPlaywright`:**
```java
// Playwright BLOCKING — harus subscribeOn boundedElastic
private Mono<byte[]> renderPdfWithPlaywright(String html) {
    return Mono.fromCallable(() -> {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(
                 new BrowserType.LaunchOptions().setHeadless(true));
             Page page = browser.newPage()) {

            page.setContent(html);
            return page.pdf(new Page.PdfOptions()
                .setFormat("A4")
                .setLandscape(true)
                .setMargin(new Margin()
                    .setTop("15mm").setBottom("15mm")
                    .setLeft("15mm").setRight("15mm")));
        }
    }).subscribeOn(Schedulers.boundedElastic());
}
```

**`parseAndManipulateHtml`:**
```java
// CPU-bound — subscribeOn boundedElastic
public Mono<Document> parseAndManipulateHtml(String htmlContent) {
    return Mono.fromCallable(() -> {
        Document document = Jsoup.parse(htmlContent);
        removeScriptTag(document);
        insertingImages(document);
        removePrintButtons(document);
        fixSignatureGrid(document);
        return document;
    }).subscribeOn(Schedulers.boundedElastic());
}
```

### PDFReader.java

PDFBox adalah blocking I/O. Bungkus:
```java
// LAMA
public String generateIDNumber(byte[] object)

// BARU
public Mono<String> generateIDNumber(byte[] object) {
    return Mono.fromCallable(() -> { /* existing logic */ })
               .subscribeOn(Schedulers.boundedElastic());
}
```

### Caller Sites yang Terdampak

Semua code yang memanggil `generateHtmlContent()` / `generatePdfBytes()` perlu diupdate ke `.flatMap()` chain.
Cari dengan grep: `generateHtmlContent\|generatePdfBytes\|parsingHtmlContent`

---

## File Summary Tambahan

| Status | File | Phase |
|--------|------|-------|
| UPDATE | `pom.xml` | 6, 7, 8 |
| UPDATE | 11 entity files | 6 |
| UPDATE | 9 repository files | 6 |
| UPDATE | seluruh service layer | 6 |
| TULIS ULANG | `configuration/S3ClientConfiguration.java` | 7 |
| UPDATE | `core/service/slik/SendNotificationSlikUpdated.java` | 7 |
| UPDATE | `core/service/slik/GenerateMetadataSlikForUncompletedDocument.java` | 7 |
| UPDATE | `platform/whatsapp/service/slik/SlikService.java` | 7 |
| TULIS ULANG | `core/service/slik/GeneratePdfFiles.java` | 8 |
| UPDATE | `core/service/slik/PDFReader.java` | 8 |
| HAPUS | iText, Flying Saucer import/usage | 8 |

---

## Risiko & Catatan Tambahan

1. **MongoDB tidak support JOIN / subquery** — `findUnpaidBillsByBranch()` yang pakai `NOT IN (SELECT ...)` harus direwrite: fetch semua paying IDs dulu → `$nin` filter. Untuk dataset besar, pertimbangkan index dan batching.

2. **Migrasi data** — Perlu skrip ETL dari MySQL ke MongoDB. Struktur tabel tagihan flat cocok untuk dokumen MongoDB. Relasi (jika ada) perlu didenormalisasi atau gunakan `DBRef`.

3. **Playwright di Docker** — Image `amazoncorretto:21-al2023-jdk` tidak punya Chromium. Harus install di Dockerfile atau ganti ke image yang support (`mcr.microsoft.com/playwright/java` atau install dependencies manual di AL2023).

4. **Playwright instance management** — Jangan buat `Playwright.create()` per request karena berat. Gunakan singleton `Playwright` + `Browser` yang di-inject sebagai Spring Bean, buat `Page` baru per request.

5. **S3 multi-page list** — `listObjectsV2` hanya return 1000 object pertama. Pagination async perlu `expand()` operator:
   ```java
   Mono.fromFuture(s3AsyncClient.listObjectsV2(req))
       .expand(resp -> resp.isTruncated()
           ? Mono.fromFuture(s3AsyncClient.listObjectsV2(req.toBuilder()
               .continuationToken(resp.nextContinuationToken()).build()))
           : Mono.empty())
       .flatMap(resp -> Flux.fromIterable(resp.contents()))
   ```

6. **`@Scheduled` + reactive** — `SendNotificationSlikUpdated.runTest()` adalah `@Scheduled` method. Dengan S3AsyncClient, harus `.block()` di dalam scheduled method atau switch ke scheduler yang aware reactive (Reactor's `Flux.interval()`).

7. **Playwright HTML untuk SLIK** — HTML yang dimanipulasi Jsoup sudah bersih (tanpa script, dengan logo, signature table). Playwright render lebih akurat tapi perlu uji apakah CSS `@page { size: A4 landscape }` dan `page-break-inside: avoid` tetap berfungsi.

---

## Status Phase

- [x] **Phase 1** — pom.xml (WebFlux)
- [x] **Phase 2** — DTO baru + hapus DTO lama
- [x] **Phase 3** — WebClientConfiguration + WhatsAppSender + WebhookController
- [x] **Phase 4** — Routers + 8 services
- [x] **Phase 5** — WhatsAppSenderService + build verification (BUILD SUCCESS 2026-04-01)
- [ ] **Phase 6** — MongoDB Reactive (entities + repositories + service layer)
- [ ] **Phase 7** — S3AsyncClient (S3ClientConfiguration + 3 files)
- [ ] **Phase 8** — GeneratePdfFiles reactive (WebClient + Playwright)
