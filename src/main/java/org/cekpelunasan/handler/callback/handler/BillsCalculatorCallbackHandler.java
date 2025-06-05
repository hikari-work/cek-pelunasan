package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.TagihanUtils;
import org.cekpelunasan.utils.button.BackKeyboardButtonForBillsUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class BillsCalculatorCallbackHandler implements CallbackProcessor {
	private final BillService billService;
	private final TagihanUtils tagihanUtils;

	public BillsCalculatorCallbackHandler(BillService billService, TagihanUtils tagihanUtils1) {
		this.billService = billService;
		this.tagihanUtils = tagihanUtils1;
	}

	@Override
	public String getCallBackData() {
		return "tagihan";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		CompletableFuture.runAsync(() -> {
			log.info("Bills Update Received");
			String[] parts = update.getCallbackQuery().getData().split("_", 5);
			Bills bills = billService.getBillById(parts[1]);
			log.info("Bill ID: {}", parts[1]);
			if (bills == null) {
				log.info("Bill ID Not Found");
				sendMessage(update.getMessage().getChatId(), "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}
			log.info("Sending Bills Message Message");
			editMessageWithMarkup(update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), tagihanUtils.detailBills(bills), telegramClient, new BackKeyboardButtonForBillsUtils().backButton(update.getCallbackQuery().getData()));
		});
		return CompletableFuture.completedFuture(null);
	}
}

