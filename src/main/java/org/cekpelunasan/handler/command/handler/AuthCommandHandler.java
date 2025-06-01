package org.cekpelunasan.handler.command.handler;

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
public class AuthCommandHandler implements CommandProcessor {

	private final AuthorizedChats authorizedChats1;
	private final UserService userService;
	private final MessageTemplate messageTemplateService;
	@Value("${telegram.bot.owner}")
	private Long ownerId;

	public AuthCommandHandler(UserService userService, MessageTemplate messageTemplateService, AuthorizedChats authorizedChats1) {
		this.userService = userService;
		this.messageTemplateService = messageTemplateService;
		this.authorizedChats1 = authorizedChats1;
	}

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
				sendMessage(chatId, messageTemplateService.notAdminUsers(), telegramClient);
				return;
			}

			if (parts.length < 2) {
				sendMessage(chatId, messageTemplateService.notValidDeauthFormat(), telegramClient);
				return;
			}

			try {
				long chatIdTarget = Long.parseLong(parts[1]);
				userService.insertNewUsers(chatIdTarget);
				authorizedChats1.addAuthorizedChat(chatIdTarget);
				sendMessage(chatIdTarget, messageTemplateService.authorizedMessage(), telegramClient);
				sendMessage(ownerId, "Sukses", telegramClient);
			} catch (NumberFormatException e) {
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
