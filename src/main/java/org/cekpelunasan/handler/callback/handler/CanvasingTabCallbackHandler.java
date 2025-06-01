package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.service.savings.SavingsService;
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

@Component
public class CanvasingTabCallbackHandler implements CallbackProcessor {
	private final SavingsService savingsService;
	private final PaginationCanvassingByTab paginationCanvassingByTab;
	private final CanvasingUtils canvasingUtils;

	public CanvasingTabCallbackHandler(SavingsService savingsService1, PaginationCanvassingByTab paginationCanvassingByTab, CanvasingUtils canvasingUtils) {
		this.savingsService = savingsService1;
		this.paginationCanvassingByTab = paginationCanvassingByTab;
		this.canvasingUtils = canvasingUtils;
	}

	@Override
	public String getCallBackData() {
		return "canvas";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
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
			StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
				.append("───────────────────\n")
				.append(String.format("📄 Halaman %s dari ", page + 1)).append(savings.getTotalPages()).append("\n\n");
			savings.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));
			InlineKeyboardMarkup markup = paginationCanvassingByTab.dynamicButtonName(savings, page, query);
			editMessageWithMarkup(update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), message.toString(), telegramClient, markup);
		});
	}
}
