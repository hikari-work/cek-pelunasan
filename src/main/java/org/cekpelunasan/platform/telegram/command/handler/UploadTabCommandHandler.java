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
import reactor.core.publisher.Mono;

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
	public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
		return super.process(update, client);
	}

	@Override
	public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
		String fileUrl = CsvDownloadUtils.extractUrl(text);
		if (fileUrl == null) {
			return Mono.fromRunnable(() -> sendMessage(chatId, ERROR_FORMAT, client));
		}
		sendMessage(chatId, "⏳ Sedang memproses file CSV...", client);
		return Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
			.flatMap(filePath -> savingsService.parseCsvAndSaveIntoDatabase(filePath))
			.doOnSuccess(v -> {
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.SAVING, true));
				sendMessage(chatId, "✅ Data tabungan berhasil diperbarui", client);
			})
			.onErrorResume(e -> {
				log.error("Gagal memproses file dari URL: {}", fileUrl, e);
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.SAVING, false));
				return Mono.fromRunnable(() -> sendMessage(chatId, "❌ Gagal memproses file: " + e.getMessage(), client));
			})
			.then();
	}
}
