package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements CommandProcessor {

	private static final String START_MESSAGE = """
		👋 *PONG!!!*
		""";
	private final AuthorizedChats authService;
	private final MessageTemplate messageTemplateService;


	@Override
	public String getCommand() {
		return "/start";
	}

	@Override
	public String getDescription() {
		return """
			Mengecek Bot Apakah Aktif
			""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (authService.isAuthorized(chatId)) {
				sendMessage(chatId, START_MESSAGE, telegramClient);
			} else {
				sendMessage(chatId, messageTemplateService.unathorizedMessage(), telegramClient);
			}
		});
	}
}
