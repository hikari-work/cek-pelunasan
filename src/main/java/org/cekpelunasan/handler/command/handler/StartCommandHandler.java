package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class StartCommandHandler implements CommandProcessor {

	private final TelegramClient telegramClient;
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
		return "Mengecek Bot Apakah Aktif";
	}

	@Override
	public CompletableFuture<Void> process(Update update) {
		try {
			log.info("Start Command Received");

			long chatId = update.getMessage().getChatId();
			String messageText = START_MESSAGE;

			if (!authService.isAuthorized(chatId)) {
				messageText = messageTemplateService.unathorizedMessage();
			}

			sendMessage(chatId, messageText, telegramClient);
			log.info("Start message sent to chat: {}", chatId);

		} catch (Exception e) {
			log.error("Error processing start command", e);
		}
		return null;
	}

	private void sendMessage(long chatId, String text, TelegramClient telegramClient) {
		try {
			SendMessage message = SendMessage.builder()
					.chatId(chatId)
					.text(text)
					.parseMode("Markdown")
					.build();

			telegramClient.executeAsync(message);
			log.debug("Message sent to chat: {}", chatId);
		} catch (Exception e) {
			log.error("Failed to send message to chat: {}", chatId, e);
		}
	}
}