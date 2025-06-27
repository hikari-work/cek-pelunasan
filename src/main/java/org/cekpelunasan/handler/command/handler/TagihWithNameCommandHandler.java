package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TagihWithNameCommandHandler implements CommandProcessor {

	private final BillService billService;
	private final AuthorizedChats authorizedChats;
	private final MessageTemplate messageTemplate;


	@Override
	public String getCommand() {
		return "/tgnama";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan list nama yang anda cari jika anda tidak mengetahui ID SPK";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!authorizedChats.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
				return;
			}

			String name = extractName(text, chatId, telegramClient);
			if (name == null) return;

			Set<String> branches = billService.lisAllBranch();

			if (branches.isEmpty()) {
				sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
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
			sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /tgnama 1234567890", telegramClient);
			return null;
		}

		return parts[1].trim();
	}

	private void sendMessageWithBranchSelection(long chatId, String name, Set<String> branches, TelegramClient telegramClient) {
		InlineKeyboardMarkup markup = new ButtonListForSelectBranch().dynamicSelectBranch(branches, name);
		sendMessage(chatId, "⚠ *Terdapat lebih dari satu cabang dengan nama yang sama*\n\nSilakan pilih cabang yang sesuai:", telegramClient, markup);
	}

	public void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
		sendMessage(chatId, text, telegramClient, null);
	}

	private void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId.toString())
				.text(text)
				.parseMode("Markdown")
				.replyMarkup(markup)
				.build());
		} catch (Exception e) {
			log.error("❌ Gagal mengirim pesan ke chatId {}: {}", chatId, e.getMessage(), e);
		}
	}
}
