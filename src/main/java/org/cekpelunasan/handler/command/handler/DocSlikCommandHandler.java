package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DocSlikCommandHandler implements CommandProcessor {
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
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (text.length() < 2) {
				sendMessage(chatId, "Nama Harus Diisi", telegramClient);
				return;
			}
			String name = text.replace("/doc ", "");
			if (name.isEmpty()) {
				sendMessage(chatId, "Nama Harus Diisi", telegramClient);
				return;
			}
			byte[] file = s3Connector.getFile(name);
			if (file.length == 0) {
				sendMessage(chatId, "Error", telegramClient);
				return;
			}
			sendDocument(chatId, "", new InputFile(new ByteArrayInputStream(file), name), telegramClient);
		});
	}
}
