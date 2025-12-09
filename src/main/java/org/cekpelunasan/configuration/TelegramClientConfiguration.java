package org.cekpelunasan.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Configuration class for the Telegram Client.
 * <p>
 * This class configures the {@link TelegramClient} used to interact with the
 * Telegram Bot API.
 * </p>
 */
@Configuration
public class TelegramClientConfiguration {

	@Value("${telegram.bot.token}")
	private String botToken;

	/**
	 * Creates a {@link TelegramClient} bean.
	 *
	 * @return The configured {@link TelegramClient} instance.
	 */
	@Bean
	public TelegramClient telegramClient() {
		return new OkHttpTelegramClient(botToken);
	}
}
