package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.slik.GenerateMetadataSlikForUncompletedDocument;
import org.springframework.stereotype.Component;



import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class RemoveMetadataSlik extends AbstractCommandHandler {

	private final GenerateMetadataSlikForUncompletedDocument generateMetadataSlikForUncompletedDocument;


	@Override
	public String getCommand() {
		return "/remdata";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() ->{
			String key = text.replace("/remdata ", "");
			if (key.isEmpty()) {
				sendMessage(chatId, "Key Harus Diisi", client);
				return;
			}
			if (key.length() < 2) {
				sendMessage(chatId, "Key Harus Diisi lebih dari 2 karakter", client);
				return;
			}
			generateMetadataSlikForUncompletedDocument.deleteMetadata(key);
			sendMessage(chatId, "Berhasil Menghapus Metadata", client);
		});
	}
}
