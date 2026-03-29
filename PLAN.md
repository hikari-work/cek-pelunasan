Planning: Migrasi Bot API → TDLight

Gambaran Perbedaan Fundamental

┌──────────────────────┬─────────────────────────────────────────────────────┬────────────────────────────────────────────────────────────────┐
│        Aspek         │                 Bot API (sekarang)                  │                            TDLight                             │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Library              │ org.telegram:telegrambots-*                         │ it.tdlight:tdlight-java                                        │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Auth                 │ Bot Token saja                                      │ API_ID + API_HASH + Bot Token                                  │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Update type          │ Update object                                       │ TdApi.UpdateNewMessage, TdApi.UpdateNewCallbackQuery, dll      │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Client               │ TelegramClient (OkHttp)                             │ SimpleTelegramClient (JNI/TDLib)                               │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Send message         │ telegramClient.execute(SendMessage.builder()...)    │ client.send(new TdApi.SendMessage(), handler) (async callback) │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Inline keyboard      │ InlineKeyboardMarkup                                │ TdApi.ReplyMarkupInlineKeyboard                                │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Long polling/Webhook │ Manual (TelegramBotsLongPollingApplication / ngrok) │ TDLight handle sendiri (internal)                              │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Parse mode           │ .parseMode("Markdown")                              │ TdApi.TextParseModeMarkdown via ParseTextEntities              │
├──────────────────────┼─────────────────────────────────────────────────────┼────────────────────────────────────────────────────────────────┤
│ Async                │ @Async + CompletableFuture                          │ Callback-based, bisa wrap ke CompletableFuture                 │
└──────────────────────┴─────────────────────────────────────────────────────┴────────────────────────────────────────────────────────────────┘

  ---
File yang Terdampak

DIHAPUS total

configuration/TelegramClientConfiguration.java   ← OkHttpTelegramClient hilang
configuration/OkHttpConfiguration.java           ← OkHttp untuk Telegram tidak perlu
controller/LongPollingBot.java                   ← TDLight handle polling sendiri
core/lifecycle/NgrokService.java                 ← Tidak perlu setup webhook manual
core/lifecycle/OnAppStart.java                   ← TDLight handle koneksi sendiri

DITULIS ULANG TOTAL

platform/telegram/bot/TelegramBot.java               ← register addUpdateHandler TDLight
platform/telegram/service/TelegramMessageService.java ← semua API call berubah
platform/telegram/command/CommandProcessor.java       ← Update → TdApi.UpdateNewMessage
platform/telegram/command/AbstractCommandHandler.java ← semua helper berubah
platform/telegram/command/CommandHandler.java         ← routing pakai TdApi types
platform/telegram/callback/CallbackProcessor.java     ← Update → TdApi.UpdateNewCallbackQuery
platform/telegram/callback/AbstractCallbackHandler.java ← semua helper berubah
platform/telegram/callback/CallbackHandler.java       ← routing berubah
aspect/AuthorizationAspect.java                      ← Update type berubah
configuration/AsyncConfiguration.java                ← TDLight punya executor sendiri
controller/WebhookController.java                    ← hapus endpoint /webhook (keep /v2/whatsapp)
core/lifecycle/PreRun.java                           ← minor, tetap bisa pakai

DITULIS ULANG: Utils/Button (8 file)

utils/button/ButtonListForBills.java
utils/button/ButtonListForSelectBranch.java
utils/button/DirectMessageButton.java
utils/button/HelpButton.java
utils/button/BackKeyboardButtonForBillsUtils.java
utils/button/SlikButtonConfirmation.java
platform/telegram/callback/pagination/*.java   ← semua 10 file pagination

DITULIS ULANG: Semua Command Handlers (27 file)

Perubahan utama:
- Update update → TdApi.UpdateNewMessage update
- update.getMessage().getChatId() → update.message.chatId
- update.getMessage().getText() → ((TdApi.MessageText) update.message.content).text.text
- TelegramClient telegramClient → SimpleTelegramClient client

DITULIS ULANG: Semua Callback Handlers (10 file)

Perubahan utama:
- Update update → TdApi.UpdateNewCallbackQuery update
- update.getCallbackQuery().getData() → new String(((TdApi.CallbackQueryPayloadData) update.payload).data)
- update.getCallbackQuery().getMessage().getChatId() → update.chatId
- update.getCallbackQuery().getMessage().getMessageId() → update.messageId

  ---
Mapping API Kritis

// === SEKARANG (Bot API) ===
// Kirim pesan teks
telegramClient.execute(SendMessage.builder()
.chatId(chatId).text(text).parseMode("Markdown").build());

// === NANTI (TDLight) ===
// Parse text dulu, lalu kirim
TdApi.FormattedText formatted = new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
// Atau gunakan ParseTextEntities untuk Markdown:
// client.send(new TdApi.ParseTextEntities(text, new TdApi.TextParseModeMarkdown(2)), result -> {
//     TdApi.FormattedText formatted = result.get();
// });

TdApi.InputMessageText content = new TdApi.InputMessageText();
content.text = formatted;

TdApi.SendMessage req = new TdApi.SendMessage();
req.chatId = chatId;
req.inputMessageContent = content;
client.send(req, result -> { ... });

// === INLINE KEYBOARD ===
// Sekarang:
InlineKeyboardMarkup.builder().keyboard(rows).build()

// Nanti:
TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
btn.text = "Label";
btn.type = new TdApi.InlineKeyboardButtonTypeCallback("data".getBytes());

TdApi.ReplyMarkupInlineKeyboard markup = new TdApi.ReplyMarkupInlineKeyboard();
markup.rows = new TdApi.InlineKeyboardButton[][] { { btn } };

// === EDIT MESSAGE ===
// Sekarang:
telegramClient.execute(EditMessageText.builder()
.chatId(chatId).messageId(msgId).text(text).replyMarkup(markup).build());

// Nanti:
TdApi.EditMessageText req = new TdApi.EditMessageText();
req.chatId = chatId;
req.messageId = msgId;
req.inputMessageContent = content;
req.replyMarkup = markup;
client.send(req, result -> { ... });

// === SEND DOCUMENT ===
// Sekarang:
telegramClient.execute(SendDocument.builder()
.chatId(chatId).document(new InputFile(stream, fileName)).build());

// Nanti (dari bytes, harus tulis ke temp file dulu):
TdApi.InputFileLocal localFile = new TdApi.InputFileLocal("/tmp/" + fileName);
TdApi.InputMessageDocument doc = new TdApi.InputMessageDocument();
doc.document = localFile;
TdApi.SendMessage req = new TdApi.SendMessage();
req.chatId = chatId;
req.inputMessageContent = doc;
client.send(req, result -> { ... });

  ---
Env Vars Baru

┌───────────────────────┬────────────────────────────────────────────────────────┐
│       Var baru        │                       Keterangan                       │
├───────────────────────┼────────────────────────────────────────────────────────┤
│ TELEGRAM_API_ID       │ integer, dari my.telegram.org                          │
├───────────────────────┼────────────────────────────────────────────────────────┤
│ TELEGRAM_API_HASH     │ string, dari my.telegram.org                           │
├───────────────────────┼────────────────────────────────────────────────────────┤
│ TELEGRAM_SESSION_PATH │ path folder TDLib session (default: ./tdlight-session) │
└───────────────────────┴────────────────────────────────────────────────────────┘

TELEGRAM_BOT_TOKEN tetap dipakai.

  ---
Urutan Pengerjaan (8 Phase)

Phase 1 — pom.xml + konfigurasi dasar (1 file)
Phase 2 — TelegramBot + TelegramMessageService (2 file)
Phase 3 — Interface & Abstract base classes (4 file)
Phase 4 — Button/Keyboard utilities (8 file)
Phase 5 — Pagination handlers (10 file)
Phase 6 — Callback handlers (10 file)
Phase 7 — Command handlers (27 file)
Phase 8 — AOP, lifecycle cleanup, controller (5 file)

  ---
Risiko & Catatan Penting

1. API_ID + API_HASH wajib ada — harus daftar di my.telegram.org. Tanpa ini TDLight tidak bisa jalan.
2. TDLight pakai TDLib session di disk — perlu folder persistent untuk session data. Di Docker, folder ini harus di-mount sebagai volume agar session tidak hilang setiap restart.
3. client.send() bersifat async callback — tidak blocking seperti telegramClient.execute(). Semua handler perlu diadaptasi agar tidak ada hasil yang terbuang.
4. SendDocument perlu temp file — TDLight tidak bisa terima ByteArrayInputStream langsung, harus tulis dulu ke disk (/tmp/) lalu pakai TdApi.InputFileLocal.
5. Markdown parsing berbeda — TDLib Markdown v2 (TdApi.TextParseModeMarkdown(2)) berbeda dengan Bot API Markdown. Perlu dicek satu per satu apakah format teks di handler masih valid.
6. WebhookController tetap ada tapi hanya untuk endpoint /v2/whatsapp. Endpoint /webhook Telegram dihapus.
7. AuthorizationAspect perlu di-bypass atau dikerjakan ulang karena tidak lagi ada Update object di signature — perlu cara lain untuk inject chatId ke AOP.

  ---