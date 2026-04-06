package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteUserAccessCommand extends AbstractCommandHandler {

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
		return "Gunakan Command ini untuk menghapus izin user.";
	}

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ");
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidDeauthFormat(), client));
		}
		long target;
		try {
			target = Long.parseLong(parts[1]);
		} catch (NumberFormatException e) {
			return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidNumber(), client));
		}
		log.info("{} Sudah ditendang", target);
		return userService.deleteUser(target)
			.doOnSuccess(v -> {
				authorizedChats.deleteUser(target);
				sendMessage(target, messageTemplate.unathorizedMessage(), client);
				sendMessage(ownerId, "Sukses", client);
			});
	}
}
