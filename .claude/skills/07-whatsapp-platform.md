---
name: whatsapp-platform
description: Arsitektur platform WhatsApp di project cek-pelunasan — webhook flow, command handlers, dan cara menambah command baru untuk WhatsApp
---

# WhatsApp Platform

Package: `org.cekpelunasan.platform.whatsapp`

---

## Arsitektur Webhook WhatsApp

```
External Gateway (go-whatsapp-web-multidevice)
       ↓ POST /v2/whatsapp
WebhookController.java
       ↓ async (langsung return 200 OK)
Routers.java
       ↓ route by command prefix
Handler konkret (HandlerTabungan, HandlerPelunasan, dll)
       ↓
Service Layer (BillService, SavingsService, dll)
       ↓
Reply via WhatsApp Gateway API
```

**Design principle**: Immediate `200 OK` response — processing async di background untuk hindari gateway timeout.

---

## WebhookController
**File**: `controller/WebhookController.java`

```java
@RestController
@RequestMapping("/v2/whatsapp")
public class WebhookController {

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
        @RequestBody WhatsAppWebhookDTO payload
    ) {
        routers.route(payload);   // async — non-blocking
        return ResponseEntity.ok().build();  // langsung 200
    }
}
```

---

## WhatsAppWebhookDTO
**File**: `platform/whatsapp/dto/webhook/WhatsAppWebhookDTO.java`

```java
class WhatsAppWebhookDTO {
    String sender;       // nomor WA pengirim (format: 628xxx@s.whatsapp.net)
    String message;      // isi pesan teks
    String messageId;    // ID pesan WA
    String groupId;      // null jika bukan group
    Boolean isGroup;     // apakah pesan dari group?
    MediaInfo media;     // null jika tidak ada media

    class MediaInfo {
        String type;     // "image" | "document" | "audio"
        String url;      // URL media yang bisa didownload
        String mimeType;
        String fileName;
    }
}
```

---

## Routers (Command Router)
**File**: `platform/whatsapp/service/Routers.java`

```java
@Service
@Async
public class Routers {
    public void route(WhatsAppWebhookDTO payload) {
        String msg = payload.getMessage().trim().toLowerCase();

        if (msg.startsWith(".t ")) {
            handlerTabungan.handle(payload);
        } else if (msg.startsWith(".p ")) {
            handlerPelunasan.handle(payload);
        } else if (msg.startsWith(".s ")) {
            handlerSlik.handle(payload);
        } else if (msg.startsWith(".jb")) {
            jatuhBayarHandler.handle(payload);
        } else if (msg.matches("^\\.\\d{12}$")) {
            handleKolekCommand.handle(payload);  // hot kolek
        }
        // pesan yang tidak match: diabaikan
    }
}
```

---

## Command Handlers

### HandlerTabungan
**File**: `platform/whatsapp/service/HandlerTabungan.java`  
**Trigger**: `.t <param>`

```
.t 1234567890     → cek tabungan by nomor rekening
.t Budi Santoso   → search tabungan by nama (max 5 hasil)
```

**Flow**:
```java
void handle(WhatsAppWebhookDTO payload) {
    String param = extractParam(payload.getMessage(), ".t ");

    if (isAccountNumber(param)) {
        savingsService.findByTabId(param)
            .subscribe(savings -> sendReply(payload.getSender(),
                formatSavingsDetail(savings)));
    } else {
        savingsService.searchByName(param)
            .take(5)
            .collectList()
            .subscribe(list -> sendReply(payload.getSender(),
                formatSavingsList(list)));
    }
}
```

---

### HandlerPelunasan
**File**: `platform/whatsapp/service/HandlerPelunasan.java`  
**Trigger**: `.p <param>`  
**DTO**: `dto/PelunasanDto.java`

```
.p SPK-001        → cek detail tagihan dan status bayar
```

**Flow**:
```java
void handle(WhatsAppWebhookDTO payload) {
    String noSpk = extractParam(payload.getMessage(), ".p ");

    billService.findByNoSpk(noSpk)
        .zipWith(payingRepository.findByNoSpk(noSpk).defaultIfEmpty(new Paying()))
        .subscribe(tuple -> {
            Bills bills = tuple.getT1();
            Paying paying = tuple.getT2();
            String reply = formatPelunasanReply(bills, paying);
            sendReply(payload.getSender(), reply);
        });
}
```

---

### HandlerSlik
**File**: `platform/whatsapp/service/HandlerSlik.java`  
**Trigger**: `.s <ktp>`

```
.s 3201234567890001    → cek SLIK by nomor KTP
```

**Flow**:
```java
void handle(WhatsAppWebhookDTO payload) {
    String ktpNumber = extractParam(payload.getMessage(), ".s ");

    slikService.checkByKtp(ktpNumber)
        .subscribe(result -> {
            sendDocument(payload.getSender(), result.getPdfBytes(),
                "SLIK_" + ktpNumber + ".pdf");
        });
}
```

---

### HandleKolekCommand (Hot Kolek)
**Trigger**: `.<12_digit_angka>` (contoh: `.123456789012`)  
**Fungsi**: Quick lookup tagihan dari kode 12 digit (identifikasi cepat nasabah kolek)

---

### JatuhBayarHandler
**Trigger**: `.jb`  
**Fungsi**: Tampilkan informasi tanggal jatuh tempo pembayaran

---

## Cara Mengirim Reply WhatsApp

### Send Text Reply
```java
@Autowired
private WhatsAppGatewayService gatewayService;

// Kirim teks biasa
gatewayService.sendText(senderNumber, "Halo, ini reply!");

// Kirim dengan format (WhatsApp markdown)
gatewayService.sendText(senderNumber,
    "*Nama*: Budi Santoso\n_Saldo_: Rp 5.000.000");
```

### Send Document/PDF
```java
gatewayService.sendDocument(
    senderNumber,
    pdfBytes,
    "laporan-slik.pdf",
    "Laporan SLIK Anda"
);
```

---

## WhatsApp Markdown Format
| Format | Syntax | Hasil |
|--------|--------|-------|
| Bold | `*teks*` | **teks** |
| Italic | `_teks_` | *teks* |
| Strikethrough | `~teks~` | ~~teks~~ |
| Monospace | ` ```teks``` ` | `teks` |

---

## Cara Menambah Command WhatsApp Baru

### 1. Buat Handler Class
```java
@Service
@RequiredArgsConstructor
public class HandlerMyCommand {

    private final MyService myService;
    private final WhatsAppGatewayService gatewayService;

    public void handle(WhatsAppWebhookDTO payload) {
        String param = payload.getMessage().substring(".mycommand ".length()).trim();

        myService.process(param)
            .subscribe(result ->
                gatewayService.sendText(payload.getSender(), formatResult(result))
            );
    }

    private String formatResult(MyResult result) {
        return String.format("*Hasil*:\n%s", result.toString());
    }
}
```

### 2. Daftarkan di Routers
```java
// Tambahkan kondisi di Routers.route()
} else if (msg.startsWith(".mycommand ")) {
    handlerMyCommand.handle(payload);
}
```

---

## Konfigurasi Gateway (application.properties)
```properties
whatsapp.gateway.url=http://localhost:3000
whatsapp.gateway.token=your_token_here
whatsapp.gateway.sender=628xxx@s.whatsapp.net
```

Komunikasi ke gateway menggunakan `WebClient` (reactive HTTP client).
