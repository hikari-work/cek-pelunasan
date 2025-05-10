package org.cekpelunasan.handler.callback.pagination;

import org.cekpelunasan.entity.CreditHistory;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.CreditHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class PaginationToCanvasing implements CallbackProcessor {


	private final CreditHistoryService creditHistoryService;
	private final PaginationCanvassingButton paginationCanvassingButton;

	public PaginationToCanvasing(CreditHistoryService creditHistoryService, PaginationCanvassingButton paginationCanvassingButton) {
		this.creditHistoryService = creditHistoryService;
		this.paginationCanvassingButton = paginationCanvassingButton;
	}

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
				sendMessage(chatId, String.format("""
								Data dengan alamat %s Tidak Ditemukan
								""", address), telegramClient);
				log.info("Data Empty");
				return;
			}
			StringBuilder messageBuilder = new StringBuilder(String.format("\uD83D\uDCC4 Halaman " + (page + 1) + " dari %d\n\n", creditHistoriesPage.getTotalPages()));
			creditHistoriesPage.forEach(dto -> messageBuilder.append(String.format("""
											👤 *%s*
											╔═══════════════════════
											║ 📊 *DATA NASABAH*
											║ ├─── 🆔 CIF   : `%s`
											║ ├─── 📍 Alamat: %s
											║ └─── 📱 Kontak: %s
											╚═══════════════════════
											
											""",
							dto.getName(),
							dto.getCustomerId(),
							formatAddress(dto.getAddress()),
							formatPhone(dto.getPhone())
			)));

			System.out.println(messageBuilder);
			InlineKeyboardMarkup markup = paginationCanvassingButton.dynamicButtonName(creditHistoriesPage, page, address);
			System.out.println(markup);
			editMessageWithMarkup(chatId, update.getCallbackQuery().getMessage().getMessageId(), messageBuilder.toString(), telegramClient, markup);
		});
	}

	@Override
	public void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
		CallbackProcessor.super.sendMessage(chatId, text, telegramClient);
	}

	public void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
		try {
			telegramClient.execute(SendMessage.builder()
							.chatId(chatId.toString())
							.text(text)
							.replyMarkup(markup)
							.parseMode("Markdown")
							.build());
		} catch (Exception e) {
			log.error("Error");
		}
	}

	private String formatAddress(String address) {
		return address.length() > 40 ? address.substring(0, 37) + "..." : address;
	}

	private String formatPhone(String phone) {
		return phone == null || phone.isEmpty() ? "📵 Tidak tersedia" :
						phone.startsWith("0") ? "☎️ " + phone : "☎️ 0" + phone;
	}

}