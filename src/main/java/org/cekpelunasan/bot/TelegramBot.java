package org.cekpelunasan.bot;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.callback.CallbackHandler;
import org.cekpelunasan.handler.command.CommandHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
public class TelegramBot {

	private final TelegramClient telegramClient;

	private final CommandHandler commandHandler;
	private final CallbackHandler callbackHandler;

	/*
	Main handler for routing messages to the appropriate handler.
	Using a callback handler or using a command handler.
	Async annotation to tell JVM not to block the main thread.
	 */
	@Async
	public void startBot(Update update) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			commandHandler.handle(update);
			return;
		}
		if (update.hasCallbackQuery()) {
			// Handle a callback using the callback handler
			callbackHandler.handle(update, telegramClient);
		}


	}
}
