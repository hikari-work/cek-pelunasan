package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class StartCommandHandler extends AbstractCommandHandler {

	private static final String START_MESSAGE = "👋 *PONG!!!*\n";

	private final AuthorizedChats authService;
	private final MessageTemplate messageTemplate;

	@Override
	public String getCommand() {
		return "/start";
	}

	@Override
	public String getDescription() {
		return "Mengecek Bot Apakah Aktif";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			long chatId = update.getMessage().getChatId();
			String messageText = authService.isAuthorized(chatId) ? START_MESSAGE : messageTemplate.unathorizedMessage();
			sendMessage(chatId, messageText, telegramClient);
		});
	}
}
