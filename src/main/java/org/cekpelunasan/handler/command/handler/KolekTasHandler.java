package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.KolekTas;
import org.cekpelunasan.handler.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class KolekTasHandler implements CommandProcessor {

	private final KolekTasService kolekTasService;
	private final KolekTasUtils kolekTasUtils;
	private final PaginationKolekTas paginationKolekTas;

	@Override
	public String getCommand() {
		return "/kolektas";
	}

	@Override
	public String getDescription() {
		return "Kolek Tas";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String data = text.split(" ")[1].trim().toLowerCase();
			if (data.isEmpty()) {
				sendMessage(chatId, "Data Tidak Boleh Kosong", telegramClient);
				return;
			}
			if (isValidKelompok(data)) {
				sendMessage(chatId, "Data Tidak Valid", telegramClient);
				return;
			}
			Page<KolekTas> kolek = kolekTasService.findKolekByKelompok(data, 0, 5);
			StringBuilder stringBuilder = new StringBuilder();
			kolek.forEach(k -> stringBuilder.append(kolekTasUtils.buildKolekTas(k)));
			sendMessageAndMarkup(chatId, stringBuilder.toString(), paginationKolekTas.dynamicButtonName(kolek, 0, data), telegramClient);
		});
	}
	private boolean isValidKelompok(String text) {
		return text.matches("^[a-zA-Z]{3}\\.\\d+$\n");
	}
	private void sendMessageAndMarkup(Long chatId, String text, InlineKeyboardMarkup inlineKeyboardMarkup, TelegramClient telegramClient) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.parseMode("Markdown")
					.replyMarkup(inlineKeyboardMarkup)
				.build());
		} catch (TelegramApiException e) {
			log.info(e.getMessage());
		}
	}
}
