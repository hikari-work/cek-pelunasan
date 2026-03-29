package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.customerhistory.CustomerHistoryService;
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
public class UploadValidationCustomer extends AbstractCommandHandler {

	private static final long DELAY_BETWEEN_USERS_MS = 500;

	private final UserService userService;
	private final CustomerHistoryService customerHistoryService;

	@Override
	public String getCommand() {
		return "/validupload";
	}

	@Override
	public String getDescription() {
		return "Upload data Pelunasan terbaru";
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
				sendMessage(chatId, "❗ *Format salah.*\nGunakan `/validupload <link_csv>`", telegramClient);
				return;
			}
			List<User> allUsers = userService.findAllUsers();
			String currentDateTime = LocalDateTime.now()
					.plusHours(7)
					.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
			try {
				Path filePath = CsvDownloadUtils.downloadCsv(fileUrl);
				customerHistoryService.parseCsvAndSaveIntoDatabase(filePath);
				notifyUsers(allUsers, String.format("✅ *Update berhasil: Data Validasi CIF diperbarui pada %s*", currentDateTime), telegramClient);
			} catch (Exception e) {
				log.error("Gagal memproses file dari URL: {}", fileUrl, e);
				notifyUsers(allUsers, "⚠ *Gagal update. Data Validasi CIF, Akan dicoba ulang.*", telegramClient);
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
