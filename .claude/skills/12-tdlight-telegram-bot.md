---
name: tdlight-telegram-bot
description: TDLight bot setup di project cek-pelunasan — TelegramBot.java, update handlers, TDLib API usage, message service, dan cara kerja TDLight dibanding Bot API
---

# TDLight Telegram Bot

Package: `org.cekpelunasan.platform.telegram`

---

## TDLight vs Bot API

| Aspek | TDLight (TDLib) | Bot API |
|-------|----------------|---------|
| **Koneksi** | Direct TCP ke Telegram servers | HTTP ke api.telegram.org |
| **Session** | Persistent (file-based) | Stateless |
| **Update** | Push (real-time) | Polling atau webhook |
| **File ops** | Native upload/download | Via Bot API server |
| **Rate limits** | Lebih longgar | Strict per bot |
| **Complexity** | Lebih kompleks | Lebih mudah |

---

## TelegramBot
**File**: `platform/telegram/bot/TelegramBot.java`

```java
@Component
public class TelegramBot {

    private SimpleTelegramClient client;

    @PostConstruct
    public void init() {
        // Setup TDLight client
        TDLibSettings settings = TDLibSettings.create(new Path(sessionPath));
        settings.setApiToken(new APIToken(apiId, apiHash));
        settings.setBotToken(botToken);
        settings.setDatabaseDirectoryPath(Paths.get(sessionPath));

        SimpleAuthenticationSupplier<?> auth = AuthenticationSupplier.bot(botToken);

        client = new SimpleTelegramClientBuilder(settings)
            .addUpdateHandler(TdApi.UpdateNewMessage.class, this::handleMessage)
            .addUpdateHandler(TdApi.UpdateNewCallbackQuery.class, this::handleCallback)
            .addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::handleAuth)
            .addUpdateHandler(TdApi.UpdateMessageSendSucceeded.class, this::handleSent)
            .build(auth);
    }

    private void handleMessage(TdApi.UpdateNewMessage update) {
        TdApi.Message message = update.message;
        // Filter: hanya proses text message dari user (bukan bot)
        if (message.content instanceof TdApi.MessageText msgText) {
            String text = msgText.text.text;
            if (text.startsWith("/")) {
                commandHandler.dispatch(message);
            }
        }
    }

    private void handleCallback(TdApi.UpdateNewCallbackQuery update) {
        callbackHandler.dispatch(update);
    }

    private void handleAuth(TdApi.UpdateAuthorizationState update) {
        log.info("Auth state: {}", update.authorizationState.getClass().getSimpleName());
    }

    private void handleSent(TdApi.UpdateMessageSendSucceeded update) {
        // Map temporary message ID ke real message ID
        messageIdResolver.resolve(update.oldMessageId, update.message.id);
    }
}
```

---

## TelegramMessageService
**File**: `platform/telegram/service/TelegramMessageService.java`

### Kirim Text Message
```java
// Text biasa
public void sendMessage(Long chatId, String text) {
    TdApi.InputMessageText inputMessage = new TdApi.InputMessageText(
        new TdApi.FormattedText(text, new TdApi.TextEntity[0]),
        null, false
    );
    client.send(new TdApi.SendMessage(chatId, 0, null, null, null, inputMessage));
}

// Dengan parse mode Markdown
public void sendMarkdown(Long chatId, String markdownText) {
    client.send(new TdApi.ParseTextEntities(
        markdownText,
        new TdApi.TextParseModeMarkdown(2)
    ), result -> {
        if (result.isError()) return;
        TdApi.FormattedText formatted = result.get();
        TdApi.InputMessageText inputMsg = new TdApi.InputMessageText(formatted, null, false);
        client.send(new TdApi.SendMessage(chatId, 0, null, null, null, inputMsg));
    });
}
```

### Kirim dengan Inline Keyboard
```java
public void sendMessage(Long chatId, String text, TdApi.ReplyMarkupInlineKeyboard keyboard) {
    TdApi.InputMessageText inputMessage = new TdApi.InputMessageText(
        new TdApi.FormattedText(text, new TdApi.TextEntity[0]),
        null, false
    );
    client.send(new TdApi.SendMessage(chatId, 0, null, keyboard, null, inputMessage));
}
```

### Edit Message
```java
public void editMessage(Long chatId, Long messageId, String newText) {
    TdApi.InputMessageText newContent = new TdApi.InputMessageText(
        new TdApi.FormattedText(newText, new TdApi.TextEntity[0]),
        null, false
    );
    client.send(new TdApi.EditMessageText(chatId, messageId, null, newContent));
}
```

### Kirim Dokumen (File/PDF)
```java
public void sendDocument(Long chatId, byte[] fileBytes, String fileName, String caption) {
    // Tulis ke temp file dulu (TDLib butuh path, bukan bytes)
    Path tempFile = Files.createTempFile("tdlight_", "_" + fileName);
    Files.write(tempFile, fileBytes);

    TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal(tempFile.toString());
    TdApi.InputMessageDocument doc = new TdApi.InputMessageDocument(
        inputFile,
        null,
        false,
        new TdApi.FormattedText(caption, new TdApi.TextEntity[0])
    );

    client.send(new TdApi.SendMessage(chatId, 0, null, null, null, doc),
        result -> {
            // Cleanup temp file setelah upload
            try { Files.delete(tempFile); } catch (Exception e) { /* ignore */ }
        }
    );
}
```

### Kirim Foto
```java
public void sendPhoto(Long chatId, byte[] imageBytes, String caption) {
    Path tempFile = Files.createTempFile("tdlight_photo_", ".jpg");
    Files.write(tempFile, imageBytes);

    TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal(tempFile.toString());
    TdApi.InputMessagePhoto photo = new TdApi.InputMessagePhoto(
        inputFile, null, new int[0], 0, 0,
        new TdApi.FormattedText(caption, new TdApi.TextEntity[0]),
        0, null, false
    );

    client.send(new TdApi.SendMessage(chatId, 0, null, null, null, photo));
}
```

### Delete Message
```java
public void deleteMessage(Long chatId, Long messageId) {
    client.send(new TdApi.DeleteMessages(chatId, new long[]{messageId}, true));
}
```

---

## Inline Keyboard Builder

```java
// Cara membuat inline keyboard
TdApi.InlineKeyboardButton[][] rows = {
    // Row 1: 2 tombol
    {
        new TdApi.InlineKeyboardButton(
            "💰 Hitung Total",
            new TdApi.InlineKeyboardButtonTypeCallback(("CALC_" + noSpk).getBytes())
        ),
        new TdApi.InlineKeyboardButton(
            "📊 Simulasi",
            new TdApi.InlineKeyboardButtonTypeCallback(("SIM_" + noSpk).getBytes())
        )
    },
    // Row 2: 1 tombol full width
    {
        new TdApi.InlineKeyboardButton(
            "❌ Tutup",
            new TdApi.InlineKeyboardButtonTypeCallback("NOCONTEXT".getBytes())
        )
    }
};

TdApi.ReplyMarkupInlineKeyboard keyboard =
    new TdApi.ReplyMarkupInlineKeyboard(rows);
```

---

## MessageIdResolver
**File**: `platform/telegram/service/MessageIdResolver.java`

```java
// TDLib mengirim pesan dengan temporary ID dulu
// Setelah server confirm, UpdateMessageSendSucceeded memberi real ID
@Service
public class MessageIdResolver {

    private final ConcurrentHashMap<Long, Long> pendingIds = new ConcurrentHashMap<>();

    // Map old temp ID ke real ID ketika dapat konfirmasi
    public void resolve(long oldId, long newId) {
        pendingIds.put(oldId, newId);
    }

    // Ambil real ID (blocking up to 5s)
    public Long getRealId(long tempId) {
        // Wait untuk resolve (max 5 detik)
        ...
    }
}
```

---

## UploadProgressService
**File**: `platform/telegram/service/UploadProgressService.java`

```java
// Kirim/update pesan progress saat upload file besar
@Service
public class UploadProgressService {

    public Long sendProgressMessage(Long chatId, String text) {
        // Kirim pesan awal, return messageId
    }

    public void updateProgress(Long chatId, Long messageId, String newText) {
        // Edit pesan dengan progress terbaru
        messageService.editMessage(chatId, messageId, newText);
    }
}
```

**Contoh penggunaan**:
```java
Long progressMsgId = uploadProgressService.sendProgressMessage(
    chatId, "⏳ Menggenerate PDF..."
);
// ... proses ...
uploadProgressService.updateProgress(chatId, progressMsgId, "✅ PDF siap!");
```

---

## Text Formatting (Telegram MarkdownV2)

```java
// Bold: **teks** → *teks* di MarkdownV2
"*Nama*: Budi Santoso"

// Italic: _teks_
"_Catatan: ..._"

// Code: `kode`
"`SPK-001`"

// Monospace block
"```\nData tabel\n```"

// Escape karakter khusus jika tidak sengaja diformat:
// . - ( ) [ ] ~ > # + = | { } ! harus di-escape dengan \
```

---

## Error Handling TDLight

```java
// TDLib error handling dengan callback
client.send(new TdApi.SendMessage(...), result -> {
    if (result.isError()) {
        TdApi.Error error = result.getError();
        log.error("TDLib error {}: {}", error.code, error.message);

        // Retry untuk error sementara
        if (error.code == 429) { // Too Many Requests
            Thread.sleep(error.message yang contain retry_after);
            // retry...
        }
    }
});
```

---

## Perbedaan Callback vs Command Handler

| | Command Handler | Callback Handler |
|--|-----------------|-----------------|
| **Trigger** | Text message dimulai `/` | Tap inline keyboard button |
| **Update type** | `UpdateNewMessage` | `UpdateNewCallbackQuery` |
| **chatId field** | `message.chatId` | `query.senderUserId` |
| **Response** | `sendMessage()` | `editMessage()` + `answerCallback()` |
| **Data** | Text dari message | `callbackData` (bytes) |
