package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.CanvasingUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			String address = text.length() > 8 ? text.substring(8).trim() : "";
			if (address.isEmpty()) {
				sendMessage(chatId, "Format salah, silahkan gunakan /canvas <alamat>", client);
				return;
			}
			List<String> addressList = Arrays.stream(address.split(","))
				.flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());

			Page<Savings> savingsPage = savingsService.findFilteredSavings(addressList, PageRequest.of(0, 5));
			if (savingsPage.isEmpty()) {
				sendMessage(chatId, "Tidak ada data yang ditemukan", client);
				return;
			}
			StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
				.append("───────────────────\n")
				.append("📄 Halaman 1 dari ").append(savingsPage.getTotalPages()).append("\n\n");
			savingsPage.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));
			sendMessage(chatId, message.toString(), paginationCanvassingByTab.dynamicButtonName(savingsPage, 0, address), client);
		});
	}
}
