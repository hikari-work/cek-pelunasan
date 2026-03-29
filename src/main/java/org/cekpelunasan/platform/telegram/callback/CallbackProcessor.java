package org.cekpelunasan.platform.telegram.callback;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

public interface CallbackProcessor {

	String getCallBackData();

	CompletableFuture<Void> process(Update update, TelegramClient telegramClient);
}
