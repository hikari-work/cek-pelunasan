package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.KolekTas;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class KolektasCallbackHandler implements CallbackProcessor {

	private final KolekTasUtils kolekTasUtils;
	private final PaginationKolekTas paginationKolekTas;
	private final KolekTasService kolekTasService;

	public KolektasCallbackHandler(KolekTasUtils kolekTasUtils, PaginationKolekTas paginationKolekTas1, KolekTasService kolekTasService) {
		this.kolekTasUtils = kolekTasUtils;
		this.paginationKolekTas = paginationKolekTas1;
		this.kolekTasService = kolekTasService;
	}

	@Override
	public String getCallBackData() {
		return "koltas";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {

			log.info("Kolektas Received....");
			log.info("Processing");
			String data = update.getCallbackQuery().getData().split("_")[1].trim().toLowerCase();
			Long chatId = update.getCallbackQuery().getMessage().getChatId();
			int page = Integer.parseInt(update.getCallbackQuery().getData().split("_")[2]);
			Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
			if (data.isEmpty()) {
				log.info("Kolektas Parsing Text Is Not Successfull....");
				sendMessage(chatId, "Data Tidak Boleh Kosong", telegramClient);
				return;
			}
			if (isValidKelompok(data)) {
				log.info("Group Is Not Valid");
				sendMessage(chatId, "Data Tidak Valid", telegramClient);
				return;
			}
			Page<KolekTas> kolek = kolekTasService.findKolekByKelompok(data, page + 1, 5);
			StringBuilder stringBuilder = new StringBuilder();
			log.info("Sending Kolek Tas For Group {}", data);
			kolek.forEach(k -> stringBuilder.append(kolekTasUtils.buildKolekTas(k)));
			InlineKeyboardMarkup markup = paginationKolekTas.dynamicButtonName(kolek,page , data);
			editMessageWithMarkup(chatId, messageId, stringBuilder.toString(), telegramClient,markup);
		});
	}
	private boolean isValidKelompok(String text) {
		return text.matches("^[a-zA-Z]{3}\\.\\d+$\n");
	}
}
