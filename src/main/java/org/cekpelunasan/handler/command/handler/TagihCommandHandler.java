package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.TagihanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class TagihCommandHandler implements CommandProcessor {

	private final BillService billService;
	private final AuthorizedChats authorizedChats1;
	private final MessageTemplate messageTemplate;
	private final TagihanUtils tagihanUtils;


	@Override
	public String getCommand() {
		return "/tagih";
	}

	@Override
	public String getDescription() {
		return """
			Mengembalikan rincian tagihan berdasarkan
			ID SPK yang anda kirimkan
			""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = text.split(" ", 2);

			if (!authorizedChats1.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
				return;
			}
			if (parts.length < 2) {
				sendMessage(chatId, messageTemplate.notValidDeauthFormat(), telegramClient);
				return;
			}
			long start = System.currentTimeMillis();

			try {
				String customerNumber = parts[1];
				Bills bills = billService.getBillById(customerNumber);
				if (bills == null) {
					sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
					return;
				}
				sendMessage(chatId, tagihanUtils.detailBills(bills) + "\nEksekusi dalam " + (System.currentTimeMillis() - start) + " ms", telegramClient);

			} catch (Exception e) {
				log.error("Error", e);
			}
		});
	}

}