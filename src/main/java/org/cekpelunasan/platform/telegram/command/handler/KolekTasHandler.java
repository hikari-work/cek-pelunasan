package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.KolekTas;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class KolekTasHandler extends AbstractCommandHandler {

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
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ", 2);
			if (parts.length < 2 || parts[1].trim().isEmpty()) {
				sendMessage(chatId, "Data Tidak Boleh Kosong", telegramClient);
				return;
			}
			String data = parts[1].trim().toLowerCase();
			Page<KolekTas> kolek = kolekTasService.findKolekByKelompok(data, 0, 5);
			StringBuilder sb = new StringBuilder();
			kolek.forEach(k -> sb.append(kolekTasUtils.buildKolekTas(k)));
			sendMessage(chatId, sb.toString(), paginationKolekTas.dynamicButtonName(kolek, 0, data), telegramClient);
		});
	}
}
