package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ", 2);
		if (parts.length < 2 || parts[1].trim().isEmpty()) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Data Tidak Boleh Kosong", client));
		}
		String data = parts[1].trim().toLowerCase();
		return kolekTasService.findKolekByKelompok(data, 0, 5)
			.flatMap(kolek -> Mono.fromRunnable(() -> {
				StringBuilder sb = new StringBuilder();
				kolek.forEach(k -> sb.append(kolekTasUtils.buildKolekTas(k)));
				sendMessage(chatId, sb.toString(), paginationKolekTas.dynamicButtonName(kolek, 0, data), client);
			}))
			.then();
	}
}
