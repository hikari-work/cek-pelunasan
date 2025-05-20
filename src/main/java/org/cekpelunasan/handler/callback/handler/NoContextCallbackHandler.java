package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class NoContextCallbackHandler implements CallbackProcessor {
	@Override
	public String getCallBackData() {
		return "noop";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String message = "🐊 Pap Dulu Dong Maniess";
			AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
				.showAlert(true)
				.text(message)
				.callbackQueryId(update.getCallbackQuery().getId())
				.build();
			try {
				telegramClient.execute(answerCallbackQuery);
			} catch (TelegramApiException e) {
				log.warn("Error sending callback query answer: {}", e.getMessage());
			}

		});
	}
}
