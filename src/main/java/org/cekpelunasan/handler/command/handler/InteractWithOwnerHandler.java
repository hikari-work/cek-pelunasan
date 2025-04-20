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
            Message message = update.getMessage();
            if (message == null) return;

            if (message.getText().equals(".id")) {
                sendMessage(message.getChatId(), "ID Kamu `" + message.getChatId() + "`", telegramClient);
                return;
            }

            Long chatId = message.getChatId();
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
}
