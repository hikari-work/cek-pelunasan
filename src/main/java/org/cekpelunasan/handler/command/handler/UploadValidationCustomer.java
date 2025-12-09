package org.cekpelunasan.handler.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.customerhistory.CustomerHistoryService;
import org.cekpelunasan.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class UploadValidationCustomer implements CommandProcessor {

	private static final long DELAY_BETWEEN_USERS_MS = 500;

	private final UserService userService;
	private final CustomerHistoryService customerHistoryService;

	public UploadValidationCustomer(UserService userService, CustomerHistoryService customerHistoryService) {
		this.userService = userService;
		this.customerHistoryService = customerHistoryService;
	}

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
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {

			String fileUrl = extractFileUrl(text, chatId, telegramClient);
			if (fileUrl == null)
				return;

			List<User> allUsers = userService.findAllUsers();

			processFileAndNotifyUsers(fileUrl, allUsers, telegramClient);
		});
	}

	private String extractFileUrl(String text, long chatId, TelegramClient telegramClient) {
		try {
			return text.split(" ", 2)[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			sendMessage(chatId, "❗ *Format salah.*\nGunakan `/validupload <link_csv>`", telegramClient);
			return null;
		}
	}

	private void processFileAndNotifyUsers(String fileUrl, List<User> allUsers, TelegramClient telegramClient) {
		String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

		boolean success = downloadAndProcessFile(fileUrl, fileName);

		// Format current date and time
		String currentDateTime = LocalDateTime.now()
				.plusHours(7)
				.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));

		String resultMessage = success
				? String.format("✅ *Update berhasil: Data Validasi CIF diperbarui pada %s*", currentDateTime)
				: "⚠ *Gagal update. Data Validasi CIF, Akan dicoba ulang.*";

		notifyUsers(allUsers, resultMessage, telegramClient);
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

	private boolean downloadAndProcessFile(String fileUrl, String fileName) {
		try (InputStream inputStream = URI.create(fileUrl).toURL().openStream()) {
			Path outputPath = Paths.get("files", fileName);
			Files.createDirectories(outputPath.getParent());
			Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);

			if (fileName.endsWith(".csv")) {
				customerHistoryService.parseCsvAndSaveIntoDatabase(outputPath);
			}
			return true;
		} catch (Exception e) {
			log.error("❌ Gagal memproses file dari URL: {}", fileUrl, e);
			return false;
		}
	}
}