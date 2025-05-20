package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.KolekTas;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class KolekTasHandler implements CommandProcessor {
	private final KolekTasService kolekTasService;
	private final KolekTasUtils kolekTasUtils;

	public KolekTasHandler(KolekTasService kolekTasService, KolekTasUtils kolekTasUtils) {
		this.kolekTasService = kolekTasService;
		this.kolekTasUtils = kolekTasUtils;
	}

	@Override
	public String getCommand() {
		return "/kolektas";
	}

	@Override
	public String getDescription() {
		return "Kolek Tas";
	}

	@Override
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
			sendMessage(chatId, stringBuilder.toString(), telegramClient);
		});
	}
	private boolean isValidKelompok(String text) {
		return text.matches("^[a-zA-Z]{3}\\.\\d+$\n");
	}
}
