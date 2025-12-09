package org.cekpelunasan.bot;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.callback.CallbackHandler;
import org.cekpelunasan.handler.command.CommandHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Component responsible for handling incoming Telegram updates.
 * <p>
 * This class routes updates to appropriate handlers such as {@link CommandHandler} or {@link CallbackHandler}.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TelegramBot {

	private final TelegramClient telegramClient;

	private final CommandHandler commandHandler;
	private final CallbackHandler callbackHandler;

	/**
	 * Main entry point for processing Telegram updates.
	 * <p>
	 * This method is executed asynchronously to avoid blocking the main thread.
	 * It delegates the update to specific handlers based on the update type (message, callback query, etc.).
	 * </p>
	 *
	 * @param update The incoming {@link Update} object from Telegram.
	 */
	@Async
	public void startBot(Update update) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			commandHandler.handle(update);
		}
		if (update.hasCallbackQuery()) {
			// Handle a callback using the callback handler
			callbackHandler.handle(update, telegramClient);
		}
		if (update.hasInlineQuery()) {

		}


	}
}
