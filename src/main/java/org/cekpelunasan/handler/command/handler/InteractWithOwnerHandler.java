package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class InteractWithOwnerHandler implements CommandProcessor {

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    @Override
    public String getCommand() {
        return "/id";
    }

    @Override
    public String getDescription() {
        return """
                Gunakan command ini untuk generate User Id anda
                untuk kebutuhan Authorization
                """;
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            log.info("Get message {}", update.getMessage().getText());
            Message message = update.getMessage();

            if (text.equals(getCommand())) {
                sendMessage(chatId, "ID Kamu `" + chatId + "`", telegramClient);
                return;
            }
            Integer messageId = message.getMessageId();

            if (!chatId.equals(ownerId)) {
                forwardMessage(chatId, ownerId, messageId, telegramClient);
                return;
            }

            if (message.getReplyToMessage() != null &&
                    message.getReplyToMessage().getForwardFrom() != null) {
                Long originalUserId = message.getReplyToMessage().getForwardFrom().getId();
                copyMessage(ownerId, messageId, originalUserId, telegramClient);
            }
        });
    }
    public void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Gagal kirim pesan", e);
        }
    }

    public void forwardMessage(Long fromChatId, Long toChatId, Integer messageId, TelegramClient telegramClient) {
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.ForwardMessage.builder()
                    .chatId(toChatId.toString())
                    .fromChatId(fromChatId.toString())
                    .messageId(messageId)
                    .build());
        } catch (Exception e) {
            log.error("Gagal forward pesan", e);
        }
    }

    public void copyMessage(Long fromChatId, Integer messageId, Long toChatId, TelegramClient telegramClient) {
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.CopyMessage.builder()
                    .chatId(toChatId.toString())
                    .fromChatId(fromChatId.toString())
                    .messageId(messageId)
                    .build());
        } catch (Exception e) {
            log.error("Gagal copy pesan", e);
        }
    }

}
