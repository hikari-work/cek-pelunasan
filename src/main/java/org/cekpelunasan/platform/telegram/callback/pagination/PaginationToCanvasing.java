package org.cekpelunasan.platform.telegram.callback.pagination;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.CreditHistory;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationToCanvasing extends AbstractCallbackHandler {

	private final CreditHistoryService creditHistoryService;
	private final PaginationCanvassingButton paginationCanvassingButton;

	@Override
	public String getCallBackData() {
		return "canvasing";
	}

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] data = update.getCallbackQuery().getData().split("_");
			String address = data[1];
			Long chatId = update.getCallbackQuery().getMessage().getChatId();
			int page = Integer.parseInt(data[2]);
			List<String> addressList = Arrays.asList(address.split(" "));
			Page<CreditHistory> creditHistoriesPage = creditHistoryService.searchAddressByKeywords(addressList, page);
			log.info("Data = {}", update.getCallbackQuery().getData());
			if (creditHistoriesPage.isEmpty()) {
				sendMessage(chatId, String.format("Data dengan alamat %s Tidak Ditemukan\n", address), telegramClient);
				log.info("Data Empty");
				return;
			}
			StringBuilder messageBuilder = new StringBuilder(String.format("\uD83D\uDCC4 Halaman " + (page + 1) + " dari %d\n\n", creditHistoriesPage.getTotalPages()));
			creditHistoriesPage.forEach(dto -> messageBuilder.append(String.format("""
					👤 *%s*
					╔═══════════════════════
					║ 📊 *DATA NASABAH*
					║ ├─── 🆔 CIF   : `%s`
					║ ├─── 📍 Alamat : %s
					║ └─── 📱 Kontak : %s
					╚═══════════════════════

					""",
				dto.getName(),
				dto.getCustomerId(),
				formatAddress(dto.getAddress()),
				formatPhone(dto.getPhone())
			)));

			InlineKeyboardMarkup markup = paginationCanvassingButton.dynamicButtonName(creditHistoriesPage, page, address);
			editMessageWithMarkup(chatId, update.getCallbackQuery().getMessage().getMessageId(), messageBuilder.toString(), telegramClient, markup);
		});
	}

	private String formatAddress(String address) {
		return address.length() > 40 ? address.substring(0, 37) + "..." : address;
	}

	private String formatPhone(String phone) {
		return phone == null || phone.isEmpty() ? "📵 Tidak tersedia" :
			phone.startsWith("0") ? "☎️ " + phone : "☎️ 0" + phone;
	}
}
