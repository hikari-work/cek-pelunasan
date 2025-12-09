package org.cekpelunasan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.cekpelunasan.bot.TelegramBot;

/**
 * Service for handling Telegram updates via Long Polling.
 * <p>
 * This class consumes updates from Telegram using long polling and passes them
 * to the {@link TelegramBot} for processing.
 * </p>
 */
@RequiredArgsConstructor
@Service
public class LongPollingBot implements LongPollingSingleThreadUpdateConsumer {

	private final TelegramBot telegrambot;

	/**
	 * Consumes an update from Telegram.
	 *
	 * @param update The {@link Update} object received from Telegram.
	 */
	@Override
	public void consume(Update update) {
		telegrambot.startBot(update);
	}
}
