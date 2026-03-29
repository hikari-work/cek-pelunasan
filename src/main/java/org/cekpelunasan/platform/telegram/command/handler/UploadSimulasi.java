package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.simulasi.SimulasiService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String fileUrl = CsvDownloadUtils.extractUrl(text);
			if (fileUrl == null) {
				sendMessage(chatId, "Url Nya Diisi Bang", telegramClient);
				return;
			}
			List<User> users = userService.findAllUsers();
			String currentDateTime = LocalDateTime.now()
					.plusHours(7)
					.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
			try {
				Path filePath = CsvDownloadUtils.downloadCsv(fileUrl);
				simulasiService.deleteAll();
				simulasiService.parseCsv(filePath);
				notifyUsers(users, String.format("✅ *Update berhasil: Data Simulasi diperbarui pada %s*", currentDateTime), telegramClient);
			} catch (Exception e) {
				log.error("Gagal memproses file dari URL: {}", fileUrl, e);
				notifyUsers(users, "⚠ *Gagal update. Data Pelunasan, Akan dicoba ulang.*", telegramClient);
			}
		});
	}

	private void notifyUsers(List<User> users, String message, TelegramClient client) {
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
