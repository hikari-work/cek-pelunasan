---
name: telegram-callbacks
description: Semua Telegram inline keyboard callback handler di project cek-pelunasan — daftar callback, data format, dan cara menambah callback baru
---

# Telegram Callback Handlers

Package: `org.cekpelunasan.platform.telegram.callback`

---

## Arsitektur Routing Callback

```
TelegramBot.java (UpdateNewCallbackQuery)
       ↓
CallbackHandler.java (Router / Dispatcher)
       ↓
CallbackProcessor (interface) — tiap callback implement ini
       ↓
Handler konkret (e.g., BillsCalculatorCallbackHandler)
```

### Interface CallbackProcessor
```java
public interface CallbackProcessor {
    String getCallbackPrefix();   // prefix data callback, e.g. "CALC_"
    void handle(TdApi.UpdateNewCallbackQuery query);
}
```

### AbstractCallbackHandler
Base class dengan helper methods:
```java
public abstract class AbstractCallbackHandler implements CallbackProcessor {
    protected final TelegramMessageService messageService;
    protected final UserService userService;

    // Helper methods:
    protected Long getChatId(TdApi.UpdateNewCallbackQuery query);
    protected String getData(TdApi.UpdateNewCallbackQuery query); // full callback data
    protected Long getMessageId(TdApi.UpdateNewCallbackQuery query);
    protected void answerCallback(Long queryId); // acknowledge callback (remove loading)
    protected void answerCallback(Long queryId, String toast); // dengan toast message
}
```

### CallbackHandler (Router)
Dispatch berdasarkan prefix dari `callbackData`:
```
callbackData = "CALC_SPK-001" → BillsCalculatorCallbackHandler
callbackData = "CALC_NAME_Budi" → BillsByNameCalculatorCallbackHandler
callbackData = "KOLEK_next_2" → KolektasCallbackHandler
```

---

## Daftar Callback Handler (12+)

### BillsCalculatorCallbackHandler
**File**: `callback/handler/BillsCalculatorCallbackHandler.java`  
**Prefix**: `CALC_`  
**Fungsi**: Tampilkan detail kalkulasi tagihan per SPK

**Data Format**: `CALC_<noSpk>`

**Flow**:
1. Parse `noSpk` dari callback data
2. `BillService.findByNoSpk(noSpk)`
3. Hitung total tagihan (pokok + bunga + denda)
4. Edit message dengan detail + inline keyboard (minimal pay, simulasi)

---

### BillsByNameCalculatorCallbackHandler
**File**: `callback/handler/BillsByNameCalculatorCallbackHandler.java`  
**Prefix**: `CALC_NAME_`  
**Fungsi**: Kalkulasi tagihan dari hasil search by nama

**Data Format**: `CALC_NAME_<nama>`

---

### CanvasingTabCallbackHandler
**File**: `callback/handler/CanvasingTabCallbackHandler.java`  
**Prefix**: `CANVAS_`  
**Fungsi**: Navigasi tab canvasing (Tagihan / Tabungan / Kolek Tas)

**Data Format**: `CANVAS_<tab>_<page>`  
**Tab values**: `TAGIHAN`, `TABUNGAN`, `KOLEKTAS`

---

### KolektasCallbackHandler
**File**: `callback/handler/KolektasCallbackHandler.java`  
**Prefix**: `KOLEK_`  
**Fungsi**: Navigasi list kolek tas (next/prev page) + mark visited

**Data Format**:
- `KOLEK_next_<page>` — next page
- `KOLEK_prev_<page>` — prev page  
- `KOLEK_visit_<noSpk>` — tandai dikunjungi

---

### MinimalPayCallbackHandler
**File**: `callback/handler/MinimalPayCallbackHandler.java`  
**Prefix**: `MINPAY_`  
**Fungsi**: Tampilkan kalkulasi pembayaran minimal untuk nasabah

**Data Format**: `MINPAY_<noSpk>`

---

### NoContextCallbackHandler
**File**: `callback/handler/NoContextCallbackHandler.java`  
**Prefix**: `NOCONTEXT`  
**Fungsi**: Handle callback yang tidak memerlukan context (acknowledgment only)

**Data Format**: `NOCONTEXT`

---

### SavingNextButtonCallbackHandler
**File**: `callback/handler/SavingNextButtonCallbackHandler.java`  
**Prefix**: `SAV_`  
**Fungsi**: Navigasi pagination hasil search tabungan

**Data Format**:
- `SAV_next_<sessionKey>_<page>` — next page
- `SAV_prev_<sessionKey>_<page>` — prev page

---

## Format Inline Keyboard

### Membuat Keyboard dengan TDLight
```java
// Buat tombol tunggal
TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton(
    "Hitung Total",
    new TdApi.InlineKeyboardButtonTypeCallback("CALC_SPK-001".getBytes())
);

// Buat keyboard 2 baris
TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard(
    new TdApi.InlineKeyboardButton[][] {
        { btn1, btn2 },   // baris 1: 2 tombol
        { btn3 }          // baris 2: 1 tombol
    }
);

// Kirim dengan keyboard
messageService.sendMessage(chatId, "Pilih aksi:", keyboard);
```

---

## Cara Menambah Callback Handler Baru

### 1. Buat Handler Class
```java
@Component
@RequiredArgsConstructor
public class MyCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;

    @Override
    public String getCallbackPrefix() {
        return "MYPREFIX_";
    }

    @Override
    public void handle(TdApi.UpdateNewCallbackQuery query) {
        Long chatId = getChatId(query);
        Long messageId = getMessageId(query);
        String data = getData(query); // "MYPREFIX_param1_param2"
        Long queryId = query.id;

        // Parse parameter dari data
        String[] parts = data.split("_");
        String param = parts[1];

        // Acknowledge callback (wajib!)
        answerCallback(queryId);

        // Proses dan edit message
        billService.findByNoSpk(param)
            .subscribe(bills -> {
                messageService.editMessage(chatId, messageId, "Result: " + bills.getName());
            });
    }
}
```

### 2. Spring Auto-Discovery
`@Component` otomatis di-inject ke `CallbackHandler` router.

### 3. Buat Tombol di Command Handler
```java
// Di command handler, buat tombol yang trigger callback ini
TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton(
    "Cek Detail",
    new TdApi.InlineKeyboardButtonTypeCallback(("MYPREFIX_" + noSpk).getBytes())
);
```

---

## Best Practices Callback

1. **Selalu `answerCallback(queryId)`** di awal handle() — menghilangkan spinner di tombol
2. **Edit message, jangan send baru** — pakai `messageService.editMessage()` bukan `sendMessage()`
3. **Parse data defensif** — validasi panjang array sebelum akses index
4. **Async dengan Reactor** — gunakan `.subscribe()` atau return `Mono`/`Flux`, jangan `.block()`
5. **Handle not found** — selalu handle case ketika data tidak ditemukan (`switchIfEmpty`)
