package org.cekpelunasan.platform.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

public interface CommandProcessor {

	String getCommand();

	String getDescription();

	CompletableFuture<Void> process(Update update, TelegramClient telegramClient);

	default CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.completedFuture(null);
	}
}
