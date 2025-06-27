package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AuthCommandHandler implements CommandProcessor {

	private final AuthorizedChats authorizedChats1;
	private final UserService userService;
	private final MessageTemplate messageTemplateService;
	@Value("${telegram.bot.owner}")
	private Long ownerId;



	@Override
	public String getCommand() {
		return "/auth";
	}

	@Override
	public String getDescription() {
		return """
			Gunakan command ini untuk memberikan izin kepada user untuk menggunakan bot.
			""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ");

			if (chatId != ownerId) {
				log.info("Chat ID {} Trying to Auth", chatId);
				sendMessage(chatId, messageTemplateService.notAdminUsers(), telegramClient);
				return;
			}

			if (parts.length < 2) {
				log.info("Not Valid Auth Format");
				sendMessage(chatId, messageTemplateService.notValidDeauthFormat(), telegramClient);
				return;
			}

			try {
				long chatIdTarget = Long.parseLong(parts[1]);
				log.info("Trying Auth {}", chatIdTarget);
				userService.insertNewUsers(chatIdTarget);
				authorizedChats1.addAuthorizedChat(chatIdTarget);
				sendMessage(chatIdTarget, messageTemplateService.authorizedMessage(), telegramClient);
				log.info("Success Auth {}", chatIdTarget);
				sendMessage(ownerId, "Sukses", telegramClient);
			} catch (NumberFormatException e) {
				log.info("Not Valid Number To Auth");
				sendMessage(chatId, messageTemplateService.notValidNumber(), telegramClient);
			}
		});
	}

	public void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
		try {
			telegramClient.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
				.chatId(chatId.toString())
				.text(text)
				.build());
		} catch (Exception e) {
			log.error("Error Sending Message");
		}
	}
}
