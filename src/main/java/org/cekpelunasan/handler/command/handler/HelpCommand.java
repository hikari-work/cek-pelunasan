package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.utils.button.HelpButton;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class HelpCommand implements CommandProcessor {
	private final AuthorizedChats authorizedChats1;
	private final HelpButton helpButton;

	public HelpCommand(AuthorizedChats authorizedChats1, HelpButton helpButton) {
		this.authorizedChats1 = authorizedChats1;
		this.helpButton = helpButton;
	}

	@Override
	public String getCommand() {
		return "/help";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats1.isAuthorized(chatId)) {
				sendMessage(chatId, "Hanya User yang dapat menggunakan command ini", telegramClient);
				return;
			}
			String message = """
				Bot Ini Digunakan untuk mencari tagihan dan pelunasan
				""";
			sendMessage(chatId, message,helpButton.sendHelpMessage(), telegramClient);
		});
	}
}
