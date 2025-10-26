package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.slik.S3ClientConfiguration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DocSlikCommandHandler implements CommandProcessor {
	private final AuthorizedChats authorizedChats1;
	private final MessageTemplate messageTemplate;
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
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats1.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
				return;
			}
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
