package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DeleteUserAccessCommand implements CommandProcessor {

	private final AuthorizedChats authorizedChats;
	private final UserService userService;
	private final MessageTemplate messageTemplate;
	@Value("${telegram.bot.owner}")
	private Long ownerId;


	@Override
	public String getCommand() {
		return "/deauth";
	}

	@Override
	public String getDescription() {
		return """
			Gunakan Command ini untuk menghapus izin user.
			""";
	}


	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ");
			if (chatId != ownerId) {
				sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
				return;
			}
			if (parts.length < 2) {
				sendMessage(chatId, messageTemplate.notValidDeauthFormat(), telegramClient);
				return;
			}
			try {
				long target = Long.parseLong(parts[1]);
				log.info("{} Sudah ditendang", target);
				userService.deleteUser(target);
				authorizedChats.deleteUser(target);
				sendMessage(target, messageTemplate.unathorizedMessage(), telegramClient);
				sendMessage(ownerId, "Sukses", telegramClient);
			} catch (NumberFormatException e) {
				sendMessage(chatId, messageTemplate.notValidNumber(), telegramClient);
			}
		});
	}
}
