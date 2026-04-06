package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.CanvasingUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CanvasingTabCommandHandler extends AbstractCommandHandler {

	private final SavingsService savingsService;
	private final PaginationCanvassingByTab paginationCanvassingByTab;
	private final CanvasingUtils canvasingUtils;

	@Override
	public String getCommand() {
		return "/canvas";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String address = text.length() > 8 ? text.substring(8).trim() : "";
		if (address.isEmpty()) {
			return Mono.fromRunnable(() -> sendMessage(chatId, "Format salah, silahkan gunakan /canvas <alamat>", client));
		}
		List<String> addressList = Arrays.stream(address.split(","))
			.flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		return savingsService.findFilteredSavings(addressList, PageRequest.of(0, 5))
			.flatMap(savingsPage -> Mono.fromRunnable(() -> {
				if (savingsPage.isEmpty()) {
					sendMessage(chatId, "Tidak ada data yang ditemukan", client);
					return;
				}
				StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
					.append("───────────────────\n")
					.append("📄 Halaman 1 dari ").append(savingsPage.getTotalPages()).append("\n\n");
				savingsPage.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));
				sendMessage(chatId, message.toString(), paginationCanvassingByTab.dynamicButtonName(savingsPage, 0, address), client);
			}))
			.then();
	}
}
