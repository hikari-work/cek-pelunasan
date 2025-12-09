package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.cekpelunasan.service.telegram.TelegramMessageService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TagihWithNameCommandHandler implements CommandProcessor {

	private final BillService billService;
	private final TelegramMessageService telegramMessageService;

	@Override
	public String getCommand() {
		return "/tgnama";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan list nama yang anda cari jika anda tidak mengetahui ID SPK";
	}

	@Override
	@RequireAuth(roles = { AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP })
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String name = extractName(text, chatId, telegramClient);
			if (name == null)
				return;

			Set<String> branches = billService.lisAllBranch();

			if (branches.isEmpty()) {
				telegramMessageService.sendText(chatId, "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}

			if (branches.size() > 1) {
				sendMessageWithBranchSelection(chatId, name, branches, telegramClient);
			}
		});
	}

	private String extractName(String text, long chatId, TelegramClient telegramClient) {
		String[] parts = text.split(" ", 2);

		if (parts.length < 2) {
			telegramMessageService.sendText(chatId, "❌ *Format tidak valid*\n\nContoh: /tgnama 1234567890",
					telegramClient);
			return null;
		}

		return parts[1].trim();
	}

	private void sendMessageWithBranchSelection(long chatId, String name, Set<String> branches,
			TelegramClient telegramClient) {
		log.info("Data ditemukan dalam beberapa cabang: {}", branches);
		InlineKeyboardMarkup markup = new ButtonListForSelectBranch().dynamicSelectBranch(branches, name);
		telegramMessageService.sendTextWithKeyboard(chatId,
				"⚠ *Terdapat lebih dari satu cabang dengan nama yang sama*\n\nSilakan pilih cabang yang sesuai:",
				markup, telegramClient);
	}
}
