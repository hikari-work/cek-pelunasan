package org.cekpelunasan.platform.telegram.callback;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
public abstract class AbstractCallbackHandler implements CallbackProcessor {

	@Autowired
	protected TelegramMessageService telegramMessageService;

	protected void sendMessage(long chatId, String text, TelegramClient telegramClient) {
		telegramMessageService.sendText(chatId, text, telegramClient);
	}

	protected void editMessageWithMarkup(long chatId, int messageId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
		telegramMessageService.editMessageWithMarkup(chatId, messageId, text, markup, telegramClient);
	}
}
