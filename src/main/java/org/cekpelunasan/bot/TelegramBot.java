package org.cekpelunasan.bot;

import org.cekpelunasan.handler.callback.CallbackHandler;
import org.cekpelunasan.handler.command.CommandHandler;
import org.cekpelunasan.handler.inline.GeminiServiceAnswer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramBot {

	private final CommandHandler commandHandler;
	private final CallbackHandler callbackHandler;
	private final GeminiServiceAnswer geminiServiceAnswer;

	public TelegramBot(CommandHandler commandHandler, CallbackHandler callbackHandler, GeminiServiceAnswer geminiServiceAnswer1) {
		this.commandHandler = commandHandler;
		this.callbackHandler = callbackHandler;
		this.geminiServiceAnswer = geminiServiceAnswer1;
	}

	@Async
	public void startBot(Update update, TelegramClient telegramClient) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			commandHandler.handle(update, telegramClient);
		}
		if (update.hasCallbackQuery()) {
			callbackHandler.handle(update, telegramClient);
		}
		if (update.hasInlineQuery()) {
			geminiServiceAnswer.handleInlineQuery(update.getInlineQuery(), telegramClient);
		}

	}
}
