package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.users.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class KantorHandler implements CommandProcessor {

	private final AuthorizedChats authorizedChats;
	private final MessageTemplate messageTemplate;
	private final UserService userService;

	@Override
	public String getCommand() {
		return "/kantor";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
				return;
			}
			String kantor = text.replace("/kantor ", "").trim();
			if (text.equals("/kantor")) {
				String userBranch = userService.findUserBranch(chatId);
				if (userBranch == null) {
					sendMessage(chatId, "Anda Tidak terdaftar di kantor manapun", telegramClient);
					return;
				}
				sendMessage(chatId, String.format("Anda sebelumnya terdaftar di kantor %s", userBranch), telegramClient);
				return;
			}
			if (kantor.length() != 4) {
				sendMessage(chatId, "Format Kantor Tidak tepat!!!", telegramClient);
				return;
			}
			userService.saveUserBranch(chatId, kantor);
			sendMessage(chatId, String.format("Sukses mengubah kantor anda menjadi %s", kantor), telegramClient);
		});
	}
}
