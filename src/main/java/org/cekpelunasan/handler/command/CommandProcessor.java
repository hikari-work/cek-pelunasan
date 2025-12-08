package org.cekpelunasan.handler.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.cekpelunasan.service.telegram.TelegramMessageService;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

public interface CommandProcessor {

	Logger log = LoggerFactory.getLogger(CommandProcessor.class);

	String getCommand();

	String getDescription();

	default CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.completedFuture(null);
	}
	default CompletableFuture<Void> process(Update update) {
		return CompletableFuture.completedFuture(null);
	}

	default CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			Long chatId = update.getMessage().getChatId();
			String text = update.getMessage().getText();

			return process(chatId, text, telegramClient);
		}

		return CompletableFuture.completedFuture(null);
	}

 default void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
        TelegramMessageService.sendTextStatic(chatId, text, telegramClient);
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
    default void sendDocument(Long chatId, String text, InputFile inputFile, TelegramClient telegramClient) {
        // Convert InputFile to bytes may not be trivial; keep existing path for captionless doc via service
        // If caption is needed, prepend text into a separate message
        // Here we fallback to Telegram client only for InputFile variant if necessary in future
        // Prefer using TelegramMessageService.sendDocument(byte[])
        log.warn("Prefer TelegramMessageService.sendDocument(byte[]) in handlers; this default may bypass service.");
        try {
            telegramClient.execute(SendDocument.builder()
                .chatId(chatId)
                .caption(text)
                .document(inputFile)
                .build());
        } catch (TelegramApiException e) {
            log.info("Error sending document: {}", e.getMessage());
        }
    }
    default void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup, TelegramClient telegramClient) {
        TelegramMessageService.sendTextWithKeyboardStatic(chatId, text, markup, telegramClient);
    }
}
