package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class DocSlikCommandHandler extends AbstractCommandHandler {

	private final S3ClientConfiguration s3Connector;

	@Override
	public String getCommand() {
		return "/doc";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String name = text.replace("/doc ", "").trim();
		if (name.isEmpty() || name.equals("/doc")) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Nama Harus Diisi", client));
		}
		return s3Connector.getFile(name)
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "File tidak ditemukan", client)))
			.flatMap(file -> Mono.fromRunnable(() -> sendDocument(chatId, name, file, client)))
			.then();
	}
}
