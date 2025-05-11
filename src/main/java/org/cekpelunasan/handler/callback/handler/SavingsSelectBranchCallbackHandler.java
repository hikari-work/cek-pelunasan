package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.service.SavingsService;
import org.springframework.data.domain.Page;
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

	public SavingsSelectBranchCallbackHandler(SavingsService savingsService, PaginationSavingsButton paginationSavingsButton) {
		this.savingsService = savingsService;
		this.paginationSavingsButton = paginationSavingsButton;
	}

	@Override
	public String getCallBackData() {
		return "branchtab";
	}

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] data = update.getCallbackQuery().getData().split("_");
			String branchName = data[1];
			String query = data[2];
			Long chatId = update.getCallbackQuery().getMessage().getChatId();
			int messageId = update.getCallbackQuery().getMessage().getMessageId();
			Page<Savings> savings = savingsService.findByNameAndBranch(query, branchName, 0);
			if (savings.isEmpty()) {
				sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}
			InlineKeyboardMarkup markup = paginationSavingsButton.keyboardMarkup(savings, branchName, 0, query);
			editMessageWithMarkup(chatId, messageId, buildMessage(savings, 0, System.currentTimeMillis()), telegramClient, markup);
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

	public String buildMessage(Page<Savings> savings, int page, long startTime) {
		StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
			.append("───────────────────\n")
			.append("📄 Halaman ").append(page + 1).append(" dari ").append(savings.getTotalPages()).append("\n\n");

		savings.forEach(saving -> message.append("👤 *").append(saving.getName()).append("*\n")
			.append("━━━━━━━━━━━━━━━━━━\n")
			.append("📝 *Detail Rekening*\n")
			.append("▫️ No. Rek: `").append(saving.getTabId()).append("`\n")
			.append("▫️ Alamat: ").append(saving.getAddress()).append("\n\n")
			.append("💰 *Informasi Saldo*\n")
			.append("▫️ Saldo Buku: ").append(formatRupiah(saving.getBalance().add(saving.getTransaction()).longValue())).append("\n")
			.append("▫️ Min. Saldo: ").append(formatRupiah(saving.getMinimumBalance().longValue())).append("\n")
			.append("▫️ Block. Saldo: ").append(formatRupiah(saving.getBlockingBalance().longValue())).append("\n")
			.append("➡️ *Saldo Efektif*: ")
			.append(formatRupiah(saving.getBalance().add(saving.getTransaction()).longValue() -
				saving.getMinimumBalance().longValue() -
				saving.getBlockingBalance().longValue()))
			.append("\n───────────────────\n\n"));

		message.append("⏱️ _Eksekusi dalam ").append(System.currentTimeMillis() - startTime).append("ms_");
		return message.toString();
	}

}