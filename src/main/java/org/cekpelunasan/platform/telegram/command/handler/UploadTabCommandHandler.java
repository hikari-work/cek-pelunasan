package org.cekpelunasan.platform.telegram.command.handler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.event.DatabaseUpdateEvent;
import org.cekpelunasan.core.event.EventType;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;



import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadTabCommandHandler extends AbstractCommandHandler {

	private static final String ERROR_FORMAT = "❗ *Format salah.*\nGunakan `/uploadtab <link_csv>`";

	private final ApplicationEventPublisher publisher;
	private final SavingsService savingsService;

	@Override
	public String getCommand() {
		return "/uploadtab";
	}

	@Override
	public String getDescription() {
		return "Upload dan proses file CSV untuk update data tabungan";
	}

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
		return CompletableFuture.runAsync(() -> {
			String fileUrl = CsvDownloadUtils.extractUrl(text);
			if (fileUrl == null) {
				sendMessage(chatId, ERROR_FORMAT, client);
				return;
			}
			try {
				Path filePath = CsvDownloadUtils.downloadCsv(fileUrl);
				savingsService.deleteAll();
				savingsService.parseCsvAndSaveIntoDatabase(filePath);
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.SAVING, true));
			} catch (Exception e) {
				log.error("Gagal memproses file dari URL: {}", fileUrl, e);
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.SAVING, false));
			}
		});
	}
}
