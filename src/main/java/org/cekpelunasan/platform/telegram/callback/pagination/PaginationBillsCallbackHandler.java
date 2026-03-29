package org.cekpelunasan.platform.telegram.callback.pagination;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationBillsCallbackHandler extends AbstractCallbackHandler {

	private final BillService billService;
	private final ButtonListForBills buttonListForBills;

	@Override
	public String getCallBackData() {
		return "paging";
	}

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			long start = System.currentTimeMillis();
			String[] parts = update.getCallbackQuery().getData().split("_", 4);
			String query = parts[1];
			String branch = parts[2];
			int page = Integer.parseInt(parts[3]);

			long chatId = update.getCallbackQuery().getMessage().getChatId();
			int messageId = update.getCallbackQuery().getMessage().getMessageId();

			Page<Bills> bills = billService.findByNameAndBranch(query, branch, page, 5);
			if (bills.isEmpty()) {
				sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
				return;
			}

			String message = buildBillsMessage(bills, page, start);
			var markup = buttonListForBills.dynamicButtonName(bills, page, query, branch);

			editMessageWithMarkup(chatId, messageId, message, telegramClient, markup);
		});
	}

	private String buildBillsMessage(Page<Bills> bills, int page, long startTime) {
		StringBuilder builder = new StringBuilder(String.format("""
			🏦 *DAFTAR NASABAH KREDIT*
			══════════════════════
			📋 Halaman %d dari %d
			──────────────────────
			
			""", page + 1, bills.getTotalPages()));

		RupiahFormatUtils formatter = new RupiahFormatUtils();
		bills.forEach(bill -> builder.append(String.format("""
				🔷 *%s*
				┌──────────────────┐
				│ 📎 *Info Nasabah*
				│ 🆔 SPK   : `%s`
				│ 📍 Alamat: %s
				│
				│ 💰 *Info Kredit*
				│ 💎 Plafond: %s
				└──────────────────┘
				
				""",
			bill.getName(),
			bill.getNoSpk(),
			bill.getAddress(),
			formatter.formatRupiah(bill.getPlafond())
		)));

		builder.append("""
			────────────────────
			⚡️ _Tap SPK untuk menyalin_
			⏱️ _Diproses dalam %dms_
			""".formatted(System.currentTimeMillis() - startTime));

		return builder.toString();
	}
}