package org.cekpelunasan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.cekpelunasan.bot.TelegramBot;

@RequiredArgsConstructor
@Service
public class LongPollingBot implements LongPollingSingleThreadUpdateConsumer {

	private final TelegramBot telegrambot;
	@Override
	public void consume(Update update) {
		telegrambot.startBot(update);
	}
}
