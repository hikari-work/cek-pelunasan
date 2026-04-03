package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



import java.util.concurrent.CompletableFuture;

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
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			String name = text.replace("/doc ", "").trim();
			if (name.isEmpty() || name.equals("/doc")) {
				sendMessage(chatId, "Nama Harus Diisi", client);
				return;
			}
			byte[] file = s3Connector.getFile(name).block();
			if (file == null || file.length == 0) {
				sendMessage(chatId, "File tidak ditemukan", client);
				return;
			}
			sendDocument(chatId, name, file, client);
		});
	}
}
