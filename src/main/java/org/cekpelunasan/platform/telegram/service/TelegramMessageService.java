package org.cekpelunasan.platform.telegram.service;

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
			return telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.parseMode(DEFAULT_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.error("Failed to send text message to {}", chatId, e);
			return null;
		}
	}

	public Message sendTextWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard, TelegramClient telegramClient) {
		try {
			return telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.replyMarkup(keyboard)
				.parseMode(DEFAULT_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.error("Failed to send text+keyboard to {}", chatId, e);
			return null;
		}
	}

	public void editText(Long chatId, Integer messageId, String text, TelegramClient telegramClient) {
		try {
			telegramClient.execute(EditMessageText.builder()
				.chatId(chatId)
				.messageId(messageId)
				.text(text)
				.parseMode(DEFAULT_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
		}
	}

	public void editMessageWithMarkup(Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup, TelegramClient telegramClient) {
		try {
			telegramClient.execute(EditMessageText.builder()
				.chatId(chatId)
				.messageId(messageId)
				.text(text)
				.replyMarkup(markup)
				.parseMode(DEFAULT_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.error("Failed to edit message with markup {} in chat {}", messageId, chatId, e);
		}
	}

	public void delete(Long chatId, Integer messageId, TelegramClient telegramClient) {
		try {
			telegramClient.execute(DeleteMessage.builder()
				.chatId(chatId)
				.messageId(messageId)
				.build());
		} catch (Exception e) {
			log.error("Failed to delete message {} in chat {}", messageId, chatId, e);
		}
	}

	public Message sendKeyboard(long chatId, InlineKeyboardMarkup keyboard, TelegramClient telegramClient, String text) {
		try {
			return telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.replyMarkup(keyboard)
				.parseMode(DEFAULT_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.error("Failed to send keyboard to {}", chatId, e);
			return null;
		}
	}

	public void sendDocument(Long chatId, String fileName, byte[] bytes, TelegramClient telegramClient) {
		try {
			telegramClient.execute(SendDocument.builder()
				.chatId(chatId)
				.document(new InputFile(new ByteArrayInputStream(bytes), fileName))
				.build());
		} catch (Exception e) {
			log.error("Failed to send document to {} with name {}", chatId, fileName, e);
		}
	}
}
