package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AbstractCommandHandler implements CommandProcessor {

    @Autowired
    protected TelegramMessageService telegramMessageService;

    @Override
    public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        if (!(update.message.content instanceof TdApi.MessageText messageText)) {
            return CompletableFuture.completedFuture(null);
        }
        return process(update.message.chatId, messageText.text.text, client);
    }

    @Override
    public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
        return CompletableFuture.completedFuture(null);
    }

    protected void sendMessage(long chatId, String text, SimpleTelegramClient client) {
        telegramMessageService.sendText(chatId, text, client);
    }

    protected void sendMessage(long chatId, String text, TdApi.ReplyMarkupInlineKeyboard markup, SimpleTelegramClient client) {
        telegramMessageService.sendTextWithKeyboard(chatId, text, markup, client);
    }

    protected void sendDocument(long chatId, String fileName, byte[] bytes, SimpleTelegramClient client) {
        telegramMessageService.sendDocument(chatId, fileName, bytes, client);
    }

    protected void copyMessage(long fromChatId, long messageId, long toChatId, SimpleTelegramClient client) {
        try {
            TdApi.ForwardMessages fwd = new TdApi.ForwardMessages();
            fwd.chatId = toChatId;
            fwd.fromChatId = fromChatId;
            fwd.messageIds = new long[]{messageId};
            fwd.sendCopy = true;
            fwd.removeCaption = false;
            client.send(fwd, result -> {
                if (result.isError()) {
                    log.error("Failed to copy message {} from {} to {}: {}", messageId, fromChatId, toChatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to copy message {}", messageId, e);
        }
    }

    protected void forwardMessage(long fromChatId, long toChatId, long messageId, SimpleTelegramClient client) {
        try {
            TdApi.ForwardMessages fwd = new TdApi.ForwardMessages();
            fwd.chatId = toChatId;
            fwd.fromChatId = fromChatId;
            fwd.messageIds = new long[]{messageId};
            fwd.sendCopy = false;
            client.send(fwd, result -> {
                if (result.isError()) {
                    log.error("Failed to forward message {} from {} to {}: {}", messageId, fromChatId, toChatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to forward message {}", messageId, e);
        }
    }
}
