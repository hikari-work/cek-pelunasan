package org.cekpelunasan.platform.telegram.callback.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServicesCallbackHandler extends AbstractCallbackHandler {

	private final BillService billService;
	private final SavingsService savingsService;
	private final ButtonListForSelectBranch buttonListForSelectBranch;
	private final SelectSavingsBranch selectSavingsBranch;

	@Override
	public String getCallBackData() {
		return "services";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] parts = update.getCallbackQuery().getData().split("_", 3);
			if (parts.length < 3) {
				log.error("Callback data not valid: {}", update.getCallbackQuery().getData());
				sendMessage(update.getCallbackQuery().getMessage().getChatId(), "❌ *Data callback tidak valid*", telegramClient);
				return;
			}

			String service = parts[1];
			String query = parts[2];
			long chatId = update.getCallbackQuery().getMessage().getChatId();
			int messageId = update.getCallbackQuery().getMessage().getMessageId();

			log.info("Services callback: service={}, query={}", service, query);

			switch (service) {
				case "Pelunasan" -> handlePelunasan(chatId, messageId, query, telegramClient);
				case "Tabungan" -> handleTabungan(chatId, messageId, query, telegramClient);
				default -> {
					log.warn("Unknown service: {}", service);
					sendMessage(chatId, "❌ *Layanan tidak dikenali*", telegramClient);
				}
			}
		});
	}

	private void handlePelunasan(long chatId, int messageId, String query, TelegramClient telegramClient) {
		Set<String> branches = billService.lisAllBranch();
		if (branches.isEmpty()) {
			sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
			return;
		}
		editMessageWithMarkup(chatId, messageId,
			"🏦 *Pilih Cabang untuk Pelunasan*\n\nNasabah: *" + query + "*",
			telegramClient,
			buttonListForSelectBranch.dynamicSelectBranch(branches, query));
	}

	private void handleTabungan(long chatId, int messageId, String query, TelegramClient telegramClient) {
		Set<String> branches = savingsService.listAllBranch(query);
		if (branches.isEmpty()) {
			sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
			return;
		}
		editMessageWithMarkup(chatId, messageId,
			"💰 *Pilih Cabang untuk Tabungan*\n\nNasabah: *" + query + "*",
			telegramClient,
			selectSavingsBranch.dynamicSelectBranch(branches, query));
	}
}
