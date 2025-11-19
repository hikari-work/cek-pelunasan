package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.utils.CanvasingUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CanvasingTabCommandHandler implements CommandProcessor {

	private final AuthorizedChats authorizedChats1;
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
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String address = text.length() > 8 ? text.substring(8).trim() : "";

			if (!authorizedChats1.isAuthorized(chatId)) {
				log.info("User Nor Auth...");
				sendMessage(chatId, "Kamu tidak memiliki akses ke fitur ini", telegramClient);
				return;
			}

			if (address.isEmpty()) {
				log.info("Address Is Not Valid");
				sendMessage(chatId, "Format salah, silahkan gunakan /canvas <alamat>", telegramClient);
				return;
			}
			List<String> addressList = Arrays.stream(address.split(","))
				.flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());

			log.info("Searching with keywords: {}", addressList);

			Page<Savings> savingsPage = savingsService.findFilteredSavings(addressList, PageRequest.of(0, 5));

			if (savingsPage.isEmpty()) {
				log.info("Canvasing data is empty...");
				sendMessage(chatId, "Tidak ada data yang ditemukan", telegramClient);
				return;
			}
			log.info("Sending Cancasing...");

			StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
				.append("───────────────────\n")
				.append("📄 Halaman 1 dari ").append(savingsPage.getTotalPages()).append("\n\n");
			savingsPage.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));

			InlineKeyboardMarkup markup = paginationCanvassingByTab.dynamicButtonName(savingsPage, 0, address);
			sendMessage(chatId, message.toString(), markup, telegramClient);
		});
	}

	public void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup, TelegramClient telegramClient) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(text)
				.parseMode("Markdown")
				.replyMarkup(markup)
				.build());
		} catch (TelegramApiException e) {
			log.info(e.getMessage());
		}
	}
}