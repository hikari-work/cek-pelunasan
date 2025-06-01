package org.cekpelunasan.handler.callback.pagination;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.utils.TagihanUtils;
import org.cekpelunasan.utils.button.ButtonListForName;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class PaginationPelunasanCallbackHandler implements CallbackProcessor {

	private final RepaymentService repaymentService;
	private final ButtonListForName buttonListForName;
	private final TagihanUtils tagihanUtils;

	public PaginationPelunasanCallbackHandler(RepaymentService repaymentService, ButtonListForName buttonListForName, TagihanUtils tagihanUtils1) {
		this.repaymentService = repaymentService;
		this.buttonListForName = buttonListForName;
		this.tagihanUtils = tagihanUtils1;
	}

	@Override
	public String getCallBackData() {
		return "page";
	}

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {

			var callback = update.getCallbackQuery();
			long chatId = callback.getMessage().getChatId();
			int messageId = callback.getMessage().getMessageId();
			String[] data = callback.getData().split("_");

			String query = data[1];
			int page = Integer.parseInt(data[2]);

			Page<Repayment> repayments = repaymentService.findName(query, page, 5);
			if (repayments.isEmpty()) {
				sendMessage(chatId, "❌ Data tidak ditemukan.", telegramClient);
				return;
			}

			String message = buildRepaymentMessage(repayments, page);
			var keyboard = buttonListForName.dynamicButtonName(repayments, page, query);

			editMessageWithMarkup(chatId, messageId, message, telegramClient, keyboard);
		});
	}

	private String buildRepaymentMessage(Page<Repayment> repayments, int page) {
		StringBuilder builder = new StringBuilder(String.format("""
			🏦 *SISTEM INFORMASI KREDIT*
			═══════════════════════════
			📊 Halaman %d dari %d
			───────────────────────────
			
			""", page + 1, repayments.getTotalPages()));

		repayments.forEach(dto -> builder.append(tagihanUtils.getAllPelunasan(dto)));

		builder.append("""
			ℹ️ *Informasi*
			▔▔▔▔▔▔▔▔▔▔▔
			📌 _Tap SPK untuk menyalin_
			""");

		return builder.toString();
	}

}