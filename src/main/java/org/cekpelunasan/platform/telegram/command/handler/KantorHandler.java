package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



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
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			if (text.equals("/kantor")) {
				String userBranch = userService.findUserBranch(chatId).block();
				if (userBranch == null) {
					sendMessage(chatId, "Anda Tidak terdaftar di kantor manapun", client);
					return;
				}
				sendMessage(chatId, String.format("Anda sebelumnya terdaftar di kantor %s", userBranch), client);
				return;
			}
			String kantor = text.replace("/kantor ", "").trim();
			if (kantor.length() != 4) {
				sendMessage(chatId, "Format Kantor Tidak tepat!!!", client);
				return;
			}
			userService.saveUserBranch(chatId, kantor).block();
			sendMessage(chatId, String.format("Sukses mengubah kantor anda menjadi %s", kantor), client);
		});
	}
}
