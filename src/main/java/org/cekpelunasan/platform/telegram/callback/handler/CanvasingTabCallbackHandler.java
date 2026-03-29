package org.cekpelunasan.platform.telegram.callback.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.CanvasingUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
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
public class CanvasingTabCallbackHandler extends AbstractCallbackHandler {
	private final SavingsService savingsService;
	private final PaginationCanvassingByTab paginationCanvassingByTab;
	private final CanvasingUtils canvasingUtils;

	@Override
	public String getCallBackData() {
		return "canvas";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			log.info("Canvasing Request Process...");
			String[] data = update.getCallbackQuery().getData().split("_");
			String query = data[1];
			int page = Integer.parseInt(data[2]);
			List<String> address = Arrays.stream(query.split("_"))
				.flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
				.filter(s -> !s.isEmpty())
				.toList();
			Page<Savings> savings = savingsService.findFilteredSavings(address, PageRequest.of(page, 5));
			if (savings.isEmpty()) {
				sendMessage(update.getCallbackQuery().getMessage().getChatId(), "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}
			log.info("Sending Canvasing Request....");
			StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
				.append("───────────────────\n")
				.append(String.format("📄 Halaman %s dari ", page + 1)).append(savings.getTotalPages()).append("\n\n");
			savings.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));
			InlineKeyboardMarkup markup = paginationCanvassingByTab.dynamicButtonName(savings, page, query);
			editMessageWithMarkup(update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), message.toString(), telegramClient, markup);
		});
	}
}
