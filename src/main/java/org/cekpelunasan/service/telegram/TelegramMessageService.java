package org.cekpelunasan.service.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

@Slf4j
@Service
public class TelegramMessageService {

    private static final String DEFAULT_PARSE_MODE = "Markdown";

    public Message sendText(Long chatId, String text, TelegramClient telegramClient) {
        try {
            SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(DEFAULT_PARSE_MODE)
                .build();
            return telegramClient.execute(msg);
        } catch (Exception e) {
            log.error("Failed to send text message to {}", chatId, e);
            return null;
        }
    }

    public Message sendKeyboard(Long chatId, InlineKeyboardMarkup keyboard, TelegramClient telegramClient, String text) {
        try {
            SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .parseMode(DEFAULT_PARSE_MODE)
                .build();
            return telegramClient.execute(msg);
        } catch (Exception e) {
            log.error("Failed to send keyboard to {}", chatId, e);
            return null;
        }
    }

    public void editText(Long chatId, Integer messageId, String text, TelegramClient telegramClient) {
        try {
            EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .parseMode(DEFAULT_PARSE_MODE)
                .build();
            telegramClient.execute(edit);
        } catch (Exception e) {
            log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }

    public void delete(Long chatId, Integer messageId, TelegramClient telegramClient) {
        try {
            DeleteMessage del = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();
            telegramClient.execute(del);
        } catch (Exception e) {
            log.error("Failed to delete message {} in chat {}", messageId, chatId, e);
        }
    }

    public Message sendTextWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard, TelegramClient telegramClient) {
        try {
            SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .parseMode(DEFAULT_PARSE_MODE)
                .build();
            return telegramClient.execute(msg);
        } catch (Exception e) {
            log.error("Failed to send text+keyboard to {}", chatId, e);
            return null;
        }
    }

    public void sendDocument(Long chatId, String fileName, byte[] bytes, TelegramClient telegramClient) {
        try {
            SendDocument doc = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new ByteArrayInputStream(bytes), fileName))
                .build();
            telegramClient.execute(doc);
        } catch (Exception e) {
            log.error("Failed to send document to {} with name {}", chatId, fileName, e);
        }
    }

    // Static convenience methods to enable usage without injection (for default interfaces/helpers)
    public static Message sendTextStatic(Long chatId, String text, TelegramClient telegramClient) {
        try {
            SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(DEFAULT_PARSE_MODE)
                .build();
            return telegramClient.execute(msg);
        } catch (Exception e) {
            log.error("[static] Failed to send text message to {}", chatId, e);
            return null;
        }
    }

    public static Message sendTextWithKeyboardStatic(Long chatId, String text, InlineKeyboardMarkup keyboard, TelegramClient telegramClient) {
        try {
            SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .parseMode(DEFAULT_PARSE_MODE)
                .build();
            return telegramClient.execute(msg);
        } catch (Exception e) {
            log.error("[static] Failed to send text+keyboard to {}", chatId, e);
            return null;
        }
    }

    public static void sendDocumentStatic(Long chatId, String fileName, byte[] bytes, TelegramClient telegramClient) {
        try {
            SendDocument doc = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new ByteArrayInputStream(bytes), fileName))
                .build();
            telegramClient.execute(doc);
        } catch (Exception e) {
            log.error("[static] Failed to send document to {} with name {}", chatId, fileName, e);
        }
    }
}
