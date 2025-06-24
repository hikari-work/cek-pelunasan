package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.service.savings.SavingsService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.concurrent.CompletableFuture;

@Component
public class SavingsSelectBranchCallbackHandler implements CallbackProcessor {
	private final SavingsService savingsService;
	private final PaginationSavingsButton paginationSavingsButton;
	private final SavingsUtils savingsUtils;

	public SavingsSelectBranchCallbackHandler(SavingsService savingsService, PaginationSavingsButton paginationSavingsButton, SavingsUtils savingsUtils) {
		this.savingsService = savingsService;
		this.paginationSavingsButton = paginationSavingsButton;
		this.savingsUtils = savingsUtils;
	}

	@Override
	public String getCallBackData() {
		return "branchtab";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			log.info("Selecting Branch For Savings");
			String[] data = update.getCallbackQuery().getData().split("_");
			String branchName = data[1];
			String query = data[2];
			Long chatId = update.getCallbackQuery().getMessage().getChatId();
			int messageId = update.getCallbackQuery().getMessage().getMessageId();
			Page<Savings> savings = savingsService.findByNameAndBranch(query, branchName, 0);
			if (savings.isEmpty()) {
				log.info("Branch Tab is Not Found...");
				sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}
			log.info("Sending Savings Data");
			InlineKeyboardMarkup markup = paginationSavingsButton.keyboardMarkup(savings, branchName, 0, query);
			editMessageWithMarkup(chatId, messageId, savingsUtils.buildMessage(savings, 0, System.currentTimeMillis()), telegramClient, markup);
		});
	}

	public String formatRupiah(Long amount) {
		if (amount == null) return "Rp0";
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
		return df.format(amount);
	}

}