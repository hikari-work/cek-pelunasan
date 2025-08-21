package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.credithistory.CreditHistoryService;
import org.cekpelunasan.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class CreditHistoryUpdateCommandHandler implements CommandProcessor {

	private static final long DELAY_BETWEEN_USER = 500;

	private final String botOwner;
	private final MessageTemplate messageTemplate;
	private final UserService userService;
	private final CreditHistoryService creditHistoryService;

	public CreditHistoryUpdateCommandHandler(@Value("${telegram.bot.owner}") String botOwner, MessageTemplate messageTemplate, UserService userService, CreditHistoryService creditHistoryService) {
		this.botOwner = botOwner;
		this.messageTemplate = messageTemplate;
		this.userService = userService;
		this.creditHistoryService = creditHistoryService;
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
			log.info("Uplading Credit....");
			if (isNotAdmin(chatId, telegramClient)) return;

			String fileUrl = extractFileUrl(text, chatId, telegramClient);
			if (fileUrl == null) return;
			List<User> allUser = userService.findAllUsers();
			notifyUsers(allUser, "⚠ *Sedang melakukan update data, mohon jangan kirim perintah apapun...*", telegramClient);

			processFileAndNotifyUsers(fileUrl, allUser, telegramClient);
		});
	}

	private void processFileAndNotifyUsers(String fileUrl, List<User> allUsers, TelegramClient telegramClient) {
		String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
		sendMessage(allUsers.getFirst().getChatId(), "⏳ *Sedang update database canvasing*", telegramClient);

		boolean success = downloadAndProcessFile(fileUrl, fileName);
		String resultMessage = success
			? "✅ *Database berhasil di proses*"
			: "⚠ *Gagal update. Akan dicoba ulang.*";

		notifyUsers(allUsers, resultMessage, telegramClient);
	}

	private boolean isNotAdmin(long chatId, TelegramClient telegramClient) {
		if (!botOwner.equals(String.valueOf(chatId))) {
			sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
			return true;
		}
		return false;
	}

	private String extractFileUrl(String text, long chatId, TelegramClient telegramClient) {
		try {
			return text.split(" ", 2)[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			sendMessage(chatId, "❗ *Format salah.*\nGunakan `/upload <link_csv>`", telegramClient);
			return null;
		}
	}

	private void notifyUsers(List<User> users, String message, TelegramClient client) {
		users.forEach(user -> {
			sendMessage(user.getChatId(), message, client);
			delayBetweenUsers();
		});
	}

	private void delayBetweenUsers() {
		try {
			Thread.sleep(DELAY_BETWEEN_USER);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("Thread interrupted saat delay antar user", e);
		}
	}

	private boolean downloadAndProcessFile(String fileUrl, String fileName) {
		try (InputStream inputStream = new URL(fileUrl).openStream()) {
			Path outputPath = Paths.get("files", fileName);
			Files.createDirectories(outputPath.getParent());
			Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);

			if (fileName.endsWith(".csv")) {
				creditHistoryService.parseCsvAndSaveIt(outputPath);
			}
			return true;
		} catch (Exception e) {
			log.error("❌ Gagal memproses file dari URL: {}", fileUrl, e);
			return false;
		}
	}
}
