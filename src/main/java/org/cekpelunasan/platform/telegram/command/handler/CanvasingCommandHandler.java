package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.CreditHistory;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationCanvassingButton;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.cekpelunasan.utils.FormatPhoneNumberUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;



import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class CanvasingCommandHandler extends AbstractCommandHandler {

	private final CreditHistoryService creditHistoryService;
	private final PaginationCanvassingButton paginationCanvassingButton;
	private final FormatPhoneNumberUtils formatPhoneNumberUtils;

	@Override
	public String getCommand() {
		return "/canvasing";
	}

	@Override
	public String getDescription() {
		return "Mengembalikan List Nasabah yang pernah Kredit Namun tidak ambil lagi";
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
			String address = text.length() > 11 ? text.substring(11).trim() : "";
			if (address.isEmpty()) {
				sendMessage(chatId, "Alamat Harus Diisi", client);
				return;
			}
			List<String> addressList = Arrays.stream(text.split(" ")).filter(s -> !s.equals(getCommand())).toList();
			Page<CreditHistory> creditHistories = creditHistoryService.searchAddressByKeywords(addressList, 0);
			if (creditHistories.isEmpty()) {
				sendMessage(chatId, String.format("Data dengan alamat %s Tidak Ditemukan\n", address), client);
				return;
			}
			StringBuilder messageBuilder = new StringBuilder(String.format("📄 Halaman 1 dari %d\n\n", creditHistories.getTotalPages()));
			creditHistories.forEach(dto -> messageBuilder.append(String.format("""
					👤 *%s*
					 ╔═══════════════════════
					 ║ 📊 *DATA NASABAH*
					 ║ ├─── 🆔 CIF   : `%s`
					 ║ ├─── 📍 Alamat : %s
					 ║ └─── 📱 Kontak : %s
					 ╚═══════════════════════

					""",
				dto.getName().toUpperCase(),
				dto.getCustomerId(),
				dto.getAddress().length() > 35 ? dto.getAddress().substring(0, 32) + "..." : dto.getAddress(),
				formatPhoneNumberUtils.formatPhoneNumber(dto.getPhone())
			)));
			sendMessage(chatId, messageBuilder.toString(), paginationCanvassingButton.dynamicButtonName(creditHistories, 0, address), client);
		});
	}
}
