package org.cekpelunasan.platform.telegram.command;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AbstractCommandHandler implements CommandProcessor {

	@Autowired
	protected TelegramMessageService telegramMessageService;

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			return process(update.getMessage().getChatId(), update.getMessage().getText(), telegramClient);
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.completedFuture(null);
	}

	protected void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
		telegramMessageService.sendText(chatId, text, telegramClient);
	}

	protected void sendMessage(long chatId, String text, TelegramClient telegramClient) {
		telegramMessageService.sendText(chatId, text, telegramClient);
	}

	protected void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup, TelegramClient telegramClient) {
		telegramMessageService.sendTextWithKeyboard(chatId, text, markup, telegramClient);
	}

	protected void sendMessage(long chatId, String text, InlineKeyboardMarkup markup, TelegramClient telegramClient) {
		telegramMessageService.sendTextWithKeyboard(chatId, text, markup, telegramClient);
	}

	protected void sendDocument(Long chatId, String fileName, byte[] bytes, TelegramClient telegramClient) {
		telegramMessageService.sendDocument(chatId, fileName, bytes, telegramClient);
	}

	protected void copyMessage(Long fromChatId, Integer messageId, Long toChatId, TelegramClient bot) {
		try {
			bot.execute(CopyMessage.builder()
				.fromChatId(fromChatId)
				.messageId(messageId)
				.chatId(toChatId)
				.build());
		} catch (TelegramApiException e) {
			log.info("Error copying message: {}", e.getMessage());
		}
	}

	protected void forwardMessage(Long fromChatId, Long toChatId, Integer messageId, TelegramClient bot) {
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
