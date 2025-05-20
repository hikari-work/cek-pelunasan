package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.KolekTasService;
import org.cekpelunasan.service.UserService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class UploadTasHandler implements CommandProcessor {

	private static final long DELAY_BETWEEN_USERS_MS = 500;

	private final KolekTasService kolekTasService;
	private final UserService userService;
	@Value("${telegram.bot.owner}")
	private String ownerId;

	public UploadTasHandler(KolekTasService kolekTasService, UserService userService) {
		this.kolekTasService = kolekTasService;
		this.userService = userService;
	}


	@Override
	public String getCommand() {
		return "/uploadtas";
	}

	@Override
	public String getDescription() {
		return "UploadTas";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (text.equals("/uploadtas")) {
				sendMessage(chatId, "UploadTas", telegramClient);
				return;
			}
			if (Long.parseLong(ownerId) != chatId) {
				sendMessage(chatId, "Unauthorized", telegramClient);
				return;
			}
			String fileUrl = extractFileUrl(text, chatId, telegramClient);
			if (fileUrl == null) return;
			List<User> allUsers = userService.findAllUsers();
			processFileAndNotifyUsers(fileUrl, allUsers, telegramClient);

		});
	}
	private boolean downloadAndProcessFile(String fileUrl, String fileName) {
		try (InputStream inputStream = new URL(fileUrl).openStream()) {
			Path outputPath = Paths.get("files", fileName);
			Files.createDirectories(outputPath.getParent());
			Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);

			if (fileName.endsWith(".csv")) {
				kolekTasService.parseCsvAndSave(outputPath);
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
			Thread.sleep(DELAY_BETWEEN_USERS_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("Thread interrupted saat delay antar user", e);
		}
	}
	private void processFileAndNotifyUsers(String fileUrl, List<User> allUsers, TelegramClient telegramClient) {
		String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

		boolean success = downloadAndProcessFile(fileUrl, fileName);

		String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));

		String resultMessage = success
			? String.format("✅ *Update berhasil: Data Kolek Tas diperbarui pada %s*", currentDateTime)
			: "⚠ *Gagal update. Data Kolek tas, Akan dicoba ulang.*";

		notifyUsers(allUsers, resultMessage, telegramClient);
	}
	private String extractFileUrl(String text, long chatId, TelegramClient telegramClient) {
		try {
			return text.split(" ", 2)[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			sendMessage(chatId, "❗ *Format salah.*\nGunakan `/uploadtas <link_csv>`", telegramClient);
			return null;
		}
	}
}
