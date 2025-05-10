package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.SavingsService;
import org.cekpelunasan.service.UserService;
import org.springframework.beans.factory.annotation.Value;
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
public class UploadTabCommandHandler implements CommandProcessor {

	private final SavingsService savingsService;
	private final UserService userService;
	@Value("${telegram.bot.owner}")
	private String botOwner;

	public UploadTabCommandHandler(SavingsService savingsService, UserService userService) {
		this.savingsService = savingsService;
		this.userService = userService;
	}

	@Override
	public String getCommand() {
		return "/uploadtab";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (!isNotAdmin(chatId)) {
				sendMessage(chatId, "Unauthorized", telegramClient);
				return;
			}
			List<User> allUser = userService.findAllUsers();
			notifyUsers(allUser, "Sedang Upload Tabungan... \n" +
							"Silahkan tunggu beberapa saat", telegramClient);
			String fileUrl = extractFileUrl(text, chatId, telegramClient);
			processFileAndNotifyUsers(fileUrl, allUser, telegramClient);
		});
	}

	private boolean isNotAdmin(long chatId) {
		return botOwner.equals(String.valueOf(chatId));
	}

	private boolean downloadAndProcessFile(String fileUrl, String fileName) {
		try (InputStream inputStream = new URL(fileUrl).openStream()) {
			Path outputPath = Paths.get("files", fileName);
			Files.createDirectories(outputPath.getParent());
			Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);

			if (fileName.endsWith(".csv")) {
				savingsService.parseCsvAndSaveIntoDatabase(outputPath);
			}
			return true;
		} catch (Exception e) {
			log.error("❌ Gagal memproses file dari URL: {}", fileUrl, e);
			return false;
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
			int DELAY_BETWEEN_USERS_MS = 100;
			Thread.sleep(DELAY_BETWEEN_USERS_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("Thread interrupted saat delay antar user", e);
		}
	}

	private String extractFileUrl(String text, long chatId, TelegramClient telegramClient) {
		try {
			return text.split(" ", 2)[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			sendMessage(chatId, "❗ *Format salah.*\nGunakan `/upload <link_csv>`", telegramClient);
			return null;
		}
	}

	private void processFileAndNotifyUsers(String fileUrl, List<User> allUsers, TelegramClient telegramClient) {
		String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
		sendMessage(allUsers.getFirst().getChatId(), "⏳ *Sedang mengunduh dan memproses file...*", telegramClient);

		boolean success = downloadAndProcessFile(fileUrl, fileName);
		String resultMessage = success
						? String.format("✅ *File berhasil diproses:*\n\n_Eksekusi dalam %dms_", System.currentTimeMillis())
						: "⚠ *Gagal update. Akan dicoba ulang.*";

		notifyUsers(allUsers, resultMessage, telegramClient);
	}
}
