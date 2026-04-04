package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadSimulasi extends AbstractCommandHandler {

	private static final long DELAY_BETWEEN_USERS_MS = 100;

	private final UserService userService;
	private final SimulasiService simulasiService;

	@Override
	public String getCommand() {
		return "/uploadsimulasi";
	}

	@Override
	public String getDescription() {
		return "";
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
			return Mono.fromRunnable(() -> sendMessage(chatId, "Url Nya Diisi Bang", client));
		}
		String currentDateTime = LocalDateTime.now()
			.plusHours(7)
			.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
		return userService.findAllUsers()
			.collectList()
			.flatMap(users -> Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
				.flatMap(filePath -> simulasiService.parseCsv(filePath))
				.doOnSuccess(v -> notifyUsers(users, String.format("✅ *Update berhasil: Data Simulasi diperbarui pada %s*", currentDateTime), client))
				.onErrorResume(e -> {
					log.error("Gagal memproses file dari URL: {}", fileUrl, e);
					notifyUsers(users, "⚠ *Gagal update. Data Pelunasan, Akan dicoba ulang.*", client);
					return Mono.empty();
				}))
			.then();
	}

	private void notifyUsers(java.util.List<org.cekpelunasan.core.entity.User> users, String message, SimpleTelegramClient client) {
		users.forEach(user -> {
			sendMessage(user.getChatId(), message, client);
			try {
				Thread.sleep(DELAY_BETWEEN_USERS_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Thread interrupted saat delay antar user", e);
			}
		});
	}
}
