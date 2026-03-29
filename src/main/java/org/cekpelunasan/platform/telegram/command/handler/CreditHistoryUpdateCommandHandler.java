package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditHistoryUpdateCommandHandler extends AbstractCommandHandler {

	private static final long DELAY_BETWEEN_USER = 500;

	private final UserService userService;
	private final CreditHistoryService creditHistoryService;

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return super.process(update, telegramClient);
	}

	@Override
	public String getCommand() {
		return "/uploadcredit";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String fileUrl = CsvDownloadUtils.extractUrl(text);
			if (fileUrl == null) {
				sendMessage(chatId, "❗ *Format salah.*\nGunakan `/uploadcredit <link_csv>`", telegramClient);
				return;
			}
			List<User> allUsers = userService.findAllUsers();
			notifyUsers(allUsers, "⚠ *Sedang melakukan update data, mohon jangan kirim perintah apapun...*", telegramClient);
			sendMessage(allUsers.getFirst().getChatId(), "⏳ *Sedang update database canvasing*", telegramClient);
			try {
				Path filePath = CsvDownloadUtils.downloadCsv(fileUrl);
				creditHistoryService.parseCsvAndSaveIt(filePath);
				notifyUsers(allUsers, "✅ *Database berhasil di proses*", telegramClient);
			} catch (Exception e) {
				log.error("Gagal memproses file dari URL: {}", fileUrl, e);
				notifyUsers(allUsers, "⚠ *Gagal update. Akan dicoba ulang.*", telegramClient);
			}
		});
	}

	private void notifyUsers(List<User> users, String message, TelegramClient client) {
		users.forEach(user -> {
			sendMessage(user.getChatId(), message, client);
			try {
				Thread.sleep(DELAY_BETWEEN_USER);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Thread interrupted saat delay antar user", e);
			}
		});
	}
}
