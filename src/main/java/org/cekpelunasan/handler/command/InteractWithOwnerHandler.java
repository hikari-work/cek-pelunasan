package org.cekpelunasan.handler.command;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class InteractWithOwnerHandler implements CommandProcessor {

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    @Override
    public String getCommand() {
        return "/id";
    }

    @Override
    public void process(Update update, TelegramClient telegramClient) {
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
    }
}
