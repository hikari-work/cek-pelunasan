package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.DateUtils;
import org.cekpelunasan.utils.TagihanUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
public class BillsByNameCalculatorCallbackHandler implements CallbackProcessor {
	private final BillService billService;
	private final DateUtils dateUtils;
	private final PaginationBillsByNameCallbackHandler paginationBillsByNameCallbackHandler;
	private final TagihanUtils tagihanUtils;

	public BillsByNameCalculatorCallbackHandler(BillService billService, DateUtils dateUtils, PaginationBillsByNameCallbackHandler paginationBillsByNameCallbackHandler, TagihanUtils tagihanUtils1) {
		this.billService = billService;
		this.dateUtils = dateUtils;
		this.paginationBillsByNameCallbackHandler = paginationBillsByNameCallbackHandler;
		this.tagihanUtils = tagihanUtils1;
	}

	@Override
	public String getCallBackData() {
		return "pagebills";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			var callback = update.getCallbackQuery();
			var data = callback.getData();
			String[] parts = data.split("_");

			String query = parts[1];
			if (query.length() == 3 || query.length() == 4) {
				log.info("Finding Bills By {}", query);
				Page<Bills> billsPage = query.length() == 3 
					? billService.findDueDateByAccountOfficer(query, dateUtils.converterDate(LocalDateTime.now()), Integer.parseInt(parts[2]), 5)
					: billService.findBranchAndPayDown(query, dateUtils.converterDate(LocalDateTime.now()), Integer.parseInt(parts[2]), 5);

				StringBuilder sb = new StringBuilder("📅 *Tagihan Jatuh Bayar Hari Ini*\n\n");
				billsPage.forEach(bills -> sb.append(tagihanUtils.billsCompact(bills)));
				log.info("Updating Bills....");
				editMessageWithMarkup(
					callback.getMessage().getChatId(), 
					callback.getMessage().getMessageId(), 
					sb.toString(), 
					telegramClient, 
					paginationBillsByNameCallbackHandler.dynamicButtonName(billsPage, Integer.parseInt(parts[2]), query)
				);
				return;
			}
			sendMessage(callback.getMessage().getChatId(), "❌ *Data tidak ditemukan*", telegramClient);
		});
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
}