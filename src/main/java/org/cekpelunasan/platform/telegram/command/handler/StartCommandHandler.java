package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



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
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			long chatId = update.message.chatId;
			String messageText = authService.isAuthorized(chatId) ? START_MESSAGE : messageTemplate.unathorizedMessage();
			sendMessage(chatId, messageText, client);
		});
	}
}
