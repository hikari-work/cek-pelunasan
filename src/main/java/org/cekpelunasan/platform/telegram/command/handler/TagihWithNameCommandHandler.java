package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagihWithNameCommandHandler extends AbstractCommandHandler {

	private final BillService billService;

	@Override
	public String getCommand() {
		return "/tgnama";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan list nama yang anda cari jika anda tidak mengetahui ID SPK";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String[] parts = text.split(" ", 2);
		if (parts.length < 2) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /tgnama 1234567890", client));
		}
		String name = parts[1].trim();
		return billService.lisAllBranch()
			.flatMap(branches -> Mono.fromRunnable(() -> {
				if (branches.isEmpty()) {
					sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
					return;
				}
				if (branches.size() > 1) {
					log.info("Data ditemukan dalam beberapa cabang: {}", branches);
					sendMessage(chatId,
						"⚠ *Terdapat lebih dari satu cabang dengan nama yang sama*\n\nSilakan pilih cabang yang sesuai:",
						new ButtonListForSelectBranch().dynamicSelectBranch(branches, name),
						client);
				}
			}))
			.then();
	}
}
