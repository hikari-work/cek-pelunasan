package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.TagihanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagihCommandHandler extends AbstractCommandHandler {

	private final BillService billService;
	private final MessageTemplate messageTemplate;
	private final TagihanUtils tagihanUtils;

	@Override
	public String getCommand() {
		return "/tagih";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan rincian tagihan berdasarkan ID SPK yang anda kirimkan";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ", 2);
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidDeauthFormat(), client));
		}
		long start = System.currentTimeMillis();
		return billService.getBillById(parts[1])
			.switchIfEmpty(Mono.fromRunnable(() -> sendMessage(chatId, "❌ *Data tidak ditemukan*", client)))
			.flatMap(bills -> Mono.fromRunnable(() ->
				sendMessage(chatId, tagihanUtils.detailBills(bills) + "\nEksekusi dalam " + (System.currentTimeMillis() - start) + " ms", client)))
			.onErrorResume(e -> {
				log.error("Error", e);
				return Mono.empty();
			})
			.then();
	}
}
