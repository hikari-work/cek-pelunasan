---
name: miniapp
description: Telegram Mini App (WebApp) REST API di project cek-pelunasan — auth flow, endpoint, DTO, dan cara menambah endpoint baru
---

# Telegram Mini App (WebApp)

Package: `org.cekpelunasan.miniapp`  
Base URL: `/api/mini`

---

## Arsitektur Mini App

```
Telegram WebApp (Frontend JS)
       ↓ POST /api/mini/auth (initData dari Telegram)
MiniAppAuthController
       ↓ TelegramInitDataVerifier (HMAC-SHA256 validasi)
       ↓ UserRepository lookup
       ↓ MiniAppSession.generateToken()
       ↓ MiniAppSessionStore.store()
       → return { token, userInfo }

Request protected endpoint:
       ↓ GET /api/mini/canvas (Authorization: Bearer <token>)
MiniAppAuthFilter (JWT-like validation)
       ↓ MiniAppSessionStore.validate()
       ↓ inject userId ke request context
MiniApp*Controller
       ↓ Service Layer
       → return DTO
```

---

## Authentication System

### TelegramInitDataVerifier
**File**: `miniapp/auth/TelegramInitDataVerifier.java`

```java
// Validasi initData dari Telegram WebApp protocol
// Menggunakan HMAC-SHA256 dengan bot token sebagai secret
boolean verify(String initData, String botToken);

// Extract user info dari initData
TelegramUser parseUser(String initData);
```

**initData format** (dari Telegram WebApp JS API):
```
query_id=AAH...
user={"id":123456,"first_name":"Budi",...}
auth_date=1234567890
hash=abc123def...
```

### MiniAppSession
**File**: `miniapp/auth/MiniAppSession.java`

```java
// Generate token baru (UUID-based)
String generateToken(Long chatId);

// Token berisi: chatId + timestamp + random
```

### MiniAppSessionStore
**File**: `miniapp/auth/MiniAppSessionStore.java`

```java
// Simpan token → chatId mapping
void store(String token, Long chatId);

// Validasi token, return chatId atau empty
Optional<Long> validate(String token);

// Hapus token (logout)
void invalidate(String token);
```

### MiniAppAuthFilter
**File**: `miniapp/filter/MiniAppAuthFilter.java`

```java
// Spring WebFilter — intercept semua request /api/mini/** kecuali /auth
// Baca header "Authorization: Bearer <token>"
// Validasi via MiniAppSessionStore
// Inject chatId ke request attribute "chatId"
```

---

## Controllers

### MiniAppAuthController
**File**: `miniapp/controller/MiniAppAuthController.java`  
**Endpoint**: `POST /api/mini/auth`  
**Auth required**: Tidak (public)

```java
// Request
class MiniAppAuthRequest {
    String initData;  // dari Telegram WebApp JS: window.Telegram.WebApp.initData
}

// Response
class MiniAppAuthResponse {
    String token;
    UserInfoDTO userInfo;
}

// Flow
POST /api/mini/auth
Body: { "initData": "query_id=AAH...&user=...&hash=..." }

Response: {
  "token": "550e8400-e29b-...",
  "userInfo": {
    "chatId": 123456,
    "name": "Budi Santoso",
    "role": "AO",
    "branchCode": "001"
  }
}
```

---

### MiniAppCanvasController
**File**: `miniapp/controller/MiniAppCanvasController.java`  
**Endpoint**: `GET /api/mini/canvas`  
**Auth required**: Ya

```java
// Response
class CanvasSummaryDTO {
    Long totalTagihan;       // Total jumlah tagihan
    Double totalNilaiTagihan;// Total nilai (rupiah)
    Long tagihanBelumLunas; // Belum dibayar
    Long tagihanLunas;      // Sudah dibayar
    String branchCode;
    String lastUpdated;
}
```

---

### MiniAppTagihanController
**File**: `miniapp/controller/MiniAppTagihanController.java`  
**Endpoint**: `GET /api/mini/tagihan?name=<nama>`  
**Auth required**: Ya

```java
// Response
class TagihanSummaryDTO {
    String noSpk;
    String name;
    Double totalBill;      // pokok + bunga + denda
    Double principal;
    Double interest;
    Double penalty;
    Boolean isPaid;
    String accountOfficer;
    String dueDate;
}
```

---

### MiniAppTabunganController
**File**: `miniapp/controller/MiniAppTabunganController.java`  
**Endpoint**: `GET /api/mini/tabungan?name=<nama>`  
**Auth required**: Ya

```java
// Response
class TabunganSummaryDTO {
    String tabId;           // Nomor rekening
    String name;            // Nama pemilik
    Double balance;         // Saldo total
    Double availableBalance; // Saldo tersedia
    Double minBalance;      // Saldo minimum/blokir
    String branch;
}
```

---

### MiniAppKolekTasController
**File**: `miniapp/controller/MiniAppKolekTasController.java`  
**Endpoint**: `GET /api/mini/kolektas`  
**Auth required**: Ya

```java
// Response
class KolekTasSummaryDTO {
    String noSpk;
    String name;
    String accountOfficer;
    String notes;
    Boolean isVisited;
    String visitDate;
}
```

---

### MiniAppPelunasanController
**File**: `miniapp/controller/MiniAppPelunasanController.java`  
**Endpoint**: `GET /api/mini/pelunasan?noSpk=<spk>`  
**Auth required**: Ya

```java
// Response
class PelunasanDetailDTO {
    String noSpk;
    String name;
    Double principal;
    Double interest;
    Double penalty;
    Double totalBill;
    Boolean isPaid;
    LocalDateTime paidAt;
    List<SimulasiRow> simulasiRows;

    class SimulasiRow {
        Integer month;
        Double principalPayment;
        Double interestPayment;
        Double remaining;
        String dueDate;
    }
}
```

---

## DTO Classes
**Package**: `org.cekpelunasan.miniapp.dto`

| DTO | Digunakan di |
|-----|-------------|
| `MiniAppAuthRequest` | POST /api/mini/auth (input) |
| `MiniAppAuthResponse` | POST /api/mini/auth (output) |
| `UserInfoDTO` | Di dalam AuthResponse |
| `CanvasSummaryDTO` | GET /api/mini/canvas |
| `TagihanSummaryDTO` | GET /api/mini/tagihan |
| `TabunganSummaryDTO` | GET /api/mini/tabungan |
| `KolekTasSummaryDTO` | GET /api/mini/kolektas |
| `PelunasanDetailDTO` | GET /api/mini/pelunasan |

---

## CORS Configuration
**File**: `miniapp/config/MiniAppCorsConfig.java`

```java
// Allow origin dari Telegram WebApp domain
// Allow headers: Authorization, Content-Type
// Allow methods: GET, POST, OPTIONS
```

---

## Cara Menambah Endpoint Mini App Baru

### 1. Buat DTO
```java
public class MyNewDTO {
    String field1;
    Integer field2;
}
```

### 2. Buat Controller
```java
@RestController
@RequestMapping("/api/mini")
@RequiredArgsConstructor
public class MiniAppMyNewController {

    private final MyService myService;

    @GetMapping("/mynew")
    public Mono<MyNewDTO> getMyNew(ServerHttpRequest request) {
        // Ambil chatId yang di-inject oleh MiniAppAuthFilter
        Long chatId = (Long) request.getAttribute("chatId");

        return myService.getData(chatId)
            .map(data -> new MyNewDTO(data.getField1(), data.getField2()));
    }
}
```

### 3. URL otomatis terproteksi
`MiniAppAuthFilter` intercept semua `/api/mini/**` kecuali `/api/mini/auth`.

---

## Frontend Integration
```javascript
// Di Telegram WebApp HTML/JS:
const initData = window.Telegram.WebApp.initData;

// 1. Auth
const authResponse = await fetch('/api/mini/auth', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ initData })
});
const { token, userInfo } = await authResponse.json();

// 2. Call protected endpoint
const canvas = await fetch('/api/mini/canvas', {
    headers: { 'Authorization': `Bearer ${token}` }
});
const data = await canvas.json();
```
