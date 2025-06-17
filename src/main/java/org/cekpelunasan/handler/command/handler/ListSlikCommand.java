package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.slik.ListUncompletedDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ListSlikCommand implements CommandProcessor {

	private final ListUncompletedDocument listUncompletedDocument;
	@Value("${telegram.bot.owner}")
	private String owner;

	public ListSlikCommand(ListUncompletedDocument listUncompletedDocument) {
		this.listUncompletedDocument = listUncompletedDocument;
	}

	@Override
	public String getCommand() {
		return "/listslik";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		if (chatId != Long.parseLong(owner)) {
			return CompletableFuture.runAsync(() -> sendMessage(chatId, "Hanya Admin yang dapat menggunakan command ini", telegramClient));
		}
		return CompletableFuture.runAsync(() ->{
			StringBuilder message = new StringBuilder("List Slik yang belum ada datanya");
			List<String> list = listUncompletedDocument.listUncompletedDocument();
			log.info("Ada beberapa data ditemukan {}", list.size());
			for (int i = 0; i < list.size(); i++) {
				message.append("\n").append(i+1).append(". ").append(list.get(i));
			}
			sendMessage(chatId, message.toString(), telegramClient);

		});
	}
}
