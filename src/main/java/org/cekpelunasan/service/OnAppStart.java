package org.cekpelunasan.service;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.controller.LongPollingBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Component
@RequiredArgsConstructor
public class OnAppStart implements ApplicationRunner {

	@Value("${telegram.bot.token}")
	private String botToken;

	private static final Logger logger = LoggerFactory.getLogger(OnAppStart.class);

	private final NgrokService ngrokService;

	private final LongPollingBot myTelegramBot;

	@Override
	public void run(ApplicationArguments args) {
		logger.info("Checking connectivity mode...");

		boolean isWebhookActive = ngrokService.setupWebhookOrFallback();

		if (isWebhookActive) {
			logger.info(">> MODE: WEBHOOK (Ngrok Active). Long Polling disabled.");
		} else {
			logger.info(">> MODE: LONG POLLING (Ngrok Inactive/Error).");

			ngrokService.deleteWebhook();

			try {
				@SuppressWarnings("resource")
				TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
				botsApplication.registerBot(botToken, myTelegramBot);
				logger.info("Long Polling Bot started successfully!");
			} catch (Exception e) {
				logger.error("Failed to start Long Polling Bot", e);
			}
		}
	}
}