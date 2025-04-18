package org.cekpelunasan.handler.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public interface CommandProcessor {
    Logger log = LoggerFactory.getLogger(CommandProcessor.class);

    String getCommand();
    void process(Update update, TelegramClient telegramClient);
    default void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
        try {
            telegramClient.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.info("Error sending message: {}", e.getMessage());
        }
    }
    default void sendMessageWithReplyMarkup(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup replyMarkup) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(replyMarkup)
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.info("Error sending message With Markup: {}", e.getMessage());
        }
    }
    default void editMessageWithMarkup(Long chatId, int messageId, String text, TelegramClient telegramClient, InlineKeyboardMarkup replyMarkup) {
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(replyMarkup)
                    .messageId(messageId)
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.info("Error editing message With Markup: {}", e.getMessage());
        }
    }
    default void copyMessage(Long fromChatId, Integer messageId, Long toChatId, TelegramClient bot) {
        CopyMessage copy = CopyMessage.builder()
                .fromChatId(fromChatId)
                .messageId(messageId)
                .chatId(toChatId)
                .build();
        try {
            bot.execute(copy);
        } catch (TelegramApiException e) {
            log.info("Error copying message: {}", e.getMessage());
        }
    }
    default void forwardMessage(Long fromChatId, Long toChatId, Integer messageId, TelegramClient bot) {
        try {
            bot.execute(ForwardMessage.builder()
                    .fromChatId(fromChatId)
                    .chatId(toChatId)
                    .messageId(messageId)
                    .build());
        } catch (TelegramApiException e) {
            log.info("Error forwarding message: {}", e.getMessage());
        }
    }
}
