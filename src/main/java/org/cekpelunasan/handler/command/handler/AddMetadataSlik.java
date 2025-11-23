package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.slik.GenerateMetadataSlikForUncompletedDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AddMetadataSlik implements CommandProcessor {

	private final GenerateMetadataSlikForUncompletedDocument generateMetadataSlikForUncompletedDocument;

	@Override
	public String getCommand() {
		return "/adddata";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() ->{
			String key = text.replace("/adddata ", "");
			if (key.isEmpty()) {
				sendMessage(chatId, "Key Harus Diisi", telegramClient);
				return;
			}
			if (key.length() < 2) {
				sendMessage(chatId, "Key Harus Diisi lebih dari 2 karakter", telegramClient);
				return;
			}
			log.info("Generate Metadata for KTP_{} ", key);
			generateMetadataSlikForUncompletedDocument.generateMetadata(key);
			sendMessage(chatId, "Complete Delete Metadata", telegramClient);
		});
	}
}
