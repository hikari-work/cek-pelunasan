package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class KantorHandler extends AbstractCommandHandler {

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
	@RequireAuth(roles = {AccountOfficerRoles.PIMP, AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (text.equals("/kantor")) {
				String userBranch = userService.findUserBranch(chatId);
				if (userBranch == null) {
					sendMessage(chatId, "Anda Tidak terdaftar di kantor manapun", telegramClient);
					return;
				}
				sendMessage(chatId, String.format("Anda sebelumnya terdaftar di kantor %s", userBranch), telegramClient);
				return;
			}
			String kantor = text.replace("/kantor ", "").trim();
			if (kantor.length() != 4) {
				sendMessage(chatId, "Format Kantor Tidak tepat!!!", telegramClient);
				return;
			}
			userService.saveUserBranch(chatId, kantor);
			sendMessage(chatId, String.format("Sukses mengubah kantor anda menjadi %s", kantor), telegramClient);
		});
	}
}
