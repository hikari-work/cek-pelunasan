package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCommandHandler extends AbstractCommandHandler {

	private final AuthorizedChats authorizedChats;
	private final UserService userService;
	private final MessageTemplate messageTemplate;

	@Value("${telegram.bot.owner}")
	private Long ownerId;

	@Override
	public String getCommand() {
		return "/auth";
	}

	@Override
	public String getDescription() {
		return "Gunakan command ini untuk memberikan izin kepada user untuk menggunakan bot.";
	}

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ");
			if (parts.length < 2) {
				sendMessage(chatId, messageTemplate.notValidDeauthFormat(), telegramClient);
				return;
			}
			try {
				long target = Long.parseLong(parts[1]);
				log.info("Trying Auth {}", target);
				userService.insertNewUsers(target);
				authorizedChats.addAuthorizedChat(target);
				sendMessage(target, messageTemplate.authorizedMessage(), telegramClient);
				log.info("Success Auth {}", target);
				sendMessage(ownerId, "Sukses", telegramClient);
			} catch (NumberFormatException e) {
				sendMessage(chatId, messageTemplate.notValidNumber(), telegramClient);
			}
		});
	}
}
