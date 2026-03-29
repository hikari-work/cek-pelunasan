package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.event.DatabaseUpdateEvent;
import org.cekpelunasan.core.event.EventType;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillsUpdateDataHandler extends AbstractCommandHandler {

	private static final String PROCESSING_MESSAGE = "⏳ *Sedang mengunduh dan memproses file...*";
	private static final String ERROR_DOWNLOAD = "❌ Gagal memproses file";

	private final BillService billService;
	private final ApplicationEventPublisher publisher;

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	public String getCommand() {
		return "/uploadtagihan";
	}

	@Override
	public String getDescription() {
		return "Gunakan Command ini untuk upload data tagihan harian.";
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String fileUrl = CsvDownloadUtils.extractUrl(text);
			if (fileUrl == null) {
				log.warn("Invalid format from chat {}", chatId);
				return;
			}
			sendMessage(chatId, PROCESSING_MESSAGE, telegramClient);
			log.info("Command: /uploadtagihan Executed with file: {}", CsvDownloadUtils.extractFileName(fileUrl));
			try {
				Path filePath = CsvDownloadUtils.downloadCsv(fileUrl);
				billService.deleteAll();
				billService.parseCsvAndSaveIntoDatabase(filePath);
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, true));
			} catch (Exception e) {
				log.error("Error processing CSV file: {}", fileUrl, e);
				sendMessage(chatId, ERROR_DOWNLOAD, telegramClient);
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, false));
			}
		});
	}
}
