package org.cekpelunasan.platform.telegram.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TelegramMessageService {

    private static final ScheduledExecutorService FILE_CLEANUP =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tdlight-file-cleanup");
            t.setDaemon(true);
            return t;
        });

    public long sendText(long chatId, String text, SimpleTelegramClient client) {
        try {
            CompletableFuture<Long> future = new CompletableFuture<>();
            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            msg.inputMessageContent = content;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send text to {}: {}", chatId, result.getError().message);
                    future.complete(0L);
                } else {
                    future.complete(result.get().id);
                }
            });
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to send text to {}", chatId, e);
            return 0L;
        }
    }

    public long sendTextWithKeyboard(long chatId, String text, TdApi.ReplyMarkupInlineKeyboard keyboard, SimpleTelegramClient client) {
        try {
            CompletableFuture<Long> future = new CompletableFuture<>();
            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            msg.replyMarkup = keyboard;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            msg.inputMessageContent = content;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send keyboard to {}: {}", chatId, result.getError().message);
                    future.complete(0L);
                } else {
                    future.complete(result.get().id);
                }
            });
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to send keyboard to {}", chatId, e);
            return 0L;
        }
    }

    public void editText(long chatId, long messageId, String text, SimpleTelegramClient client) {
        try {
            TdApi.EditMessageText edit = new TdApi.EditMessageText();
            edit.chatId = chatId;
            edit.messageId = messageId;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            edit.inputMessageContent = content;
            client.send(edit, result -> {
                if (result.isError()) {
                    log.error("Failed to edit message {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }

    public void editMessageWithMarkup(long chatId, long messageId, String text, TdApi.ReplyMarkupInlineKeyboard markup, SimpleTelegramClient client) {
        try {
            TdApi.EditMessageText edit = new TdApi.EditMessageText();
            edit.chatId = chatId;
            edit.messageId = messageId;
            edit.replyMarkup = markup;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            edit.inputMessageContent = content;
            client.send(edit, result -> {
                if (result.isError()) {
                    log.error("Failed to edit message with markup {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to edit message with markup {} in chat {}", messageId, chatId, e);
        }
    }

    public void delete(long chatId, long messageId, SimpleTelegramClient client) {
        try {
            TdApi.DeleteMessages del = new TdApi.DeleteMessages();
            del.chatId = chatId;
            del.messageIds = new long[]{messageId};
            del.revoke = true;
            client.send(del, result -> {
                if (result.isError()) {
                    log.error("Failed to delete message {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to delete message {} in chat {}", messageId, chatId, e);
        }
    }

    public void sendDocument(long chatId, String fileName, byte[] bytes, SimpleTelegramClient client) {
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("tdlight_", "_" + fileName);
            Files.write(tmpFile, bytes);

            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = tmpFile.toAbsolutePath().toString();

            TdApi.InputMessageDocument doc = new TdApi.InputMessageDocument();
            doc.document = inputFile;
            doc.caption = new TdApi.FormattedText("", new TdApi.TextEntity[0]);

            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            msg.inputMessageContent = doc;

            final Path finalTmpFile = tmpFile;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send document to {}: {}", chatId, result.getError().message);
                }
                // Delay deletion — TDLib may retry upload after callback fires
                FILE_CLEANUP.schedule(() -> {
                    try { Files.deleteIfExists(finalTmpFile); } catch (Exception ignored) {}
                }, 5, TimeUnit.MINUTES);
            });
        } catch (Exception e) {
            log.error("Failed to send document to {}", chatId, e);
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
            }
        }
    }

    public long sendKeyboard(long chatId, TdApi.ReplyMarkupInlineKeyboard keyboard, SimpleTelegramClient client, String text) {
        return sendTextWithKeyboard(chatId, text, keyboard, client);
    }

    public TdApi.FormattedText parseMarkdown(String text, SimpleTelegramClient client) {
        try {
            CompletableFuture<TdApi.FormattedText> future = new CompletableFuture<>();
            TdApi.ParseTextEntities req = new TdApi.ParseTextEntities();
            req.text = text;
            req.parseMode = new TdApi.TextParseModeMarkdown(1);
            client.send(req, result -> {
                if (result.isError()) {
                    future.complete(new TdApi.FormattedText(text, new TdApi.TextEntity[0]));
                } else {
                    future.complete(result.get());
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
        }
    }
}
