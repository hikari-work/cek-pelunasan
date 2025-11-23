package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.slik.ListUncompletedDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ListSlikCommand implements CommandProcessor {

	private final ListUncompletedDocument listUncompletedDocument;



	@Override
	public String getCommand() {
		return "/listslik";
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
