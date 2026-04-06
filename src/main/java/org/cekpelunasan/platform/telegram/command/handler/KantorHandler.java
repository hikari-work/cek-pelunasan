package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		if (text.equals("/kantor")) {
			return userService.findUserBranch(chatId)
				.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "Anda Tidak terdaftar di kantor manapun", client)))
				.flatMap(userBranch -> Mono.fromRunnable(() ->
					sendMessage(chatId, String.format("Anda sebelumnya terdaftar di kantor %s", userBranch), client)))
				.then();
		}
		String kantor = text.replace("/kantor ", "").trim();
		if (kantor.length() != 4) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Format Kantor Tidak tepat!!!", client));
		}
		return userService.saveUserBranch(chatId, kantor)
			.doOnSuccess(v -> sendMessage(chatId, String.format("Sukses mengubah kantor anda menjadi %s", kantor), client));
	}
}
