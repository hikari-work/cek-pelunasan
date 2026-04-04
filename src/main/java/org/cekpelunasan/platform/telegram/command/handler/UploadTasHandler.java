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
import org.cekpelunasan.core.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadTasHandler extends AbstractCommandHandler {

	private static final String ERROR_FORMAT = "❗ *Format salah.*\nGunakan `/uploadtas <link_csv>`";

	private final ApplicationEventPublisher publisher;
	private final KolekTasService kolekTasService;

	@Override
	public String getCommand() {
		return "/uploadtas";
	}

	@Override
	public String getDescription() {
		return "Upload dan proses file CSV untuk update data koleksi tas";
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
		return Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
			.flatMap(filePath -> kolekTasService.parseCsvAndSave(filePath))
			.doOnSuccess(v -> {
				log.info("File berhasil diproses: {}", CsvDownloadUtils.extractFileName(fileUrl));
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.KOLEK_TAS, true));
			})
			.onErrorResume(e -> {
				log.error("Gagal memproses file dari URL: {}", fileUrl, e);
				publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.KOLEK_TAS, false));
				return Mono.empty();
			})
			.then();
	}
}
