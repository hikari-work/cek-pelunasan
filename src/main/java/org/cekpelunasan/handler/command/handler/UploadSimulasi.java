package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.simulasi.SimulasiService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class UploadSimulasi implements CommandProcessor {

	private final UserService userService;
	private final SimulasiService simulasiService;
	@Value("${telegram.bot.owner}")
	private String owner;


	@Override
	public String getCommand() {
		return "/uploadsimulasi";
	}

	@Override
	public String getDescription() {
		return "";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String url = text.split(" ", 2)[1];
			if (url == null) {
				sendMessage(chatId, "Url Nya Diisi Bang", telegramClient);
				return;
			}
			if (!String.valueOf(chatId).equals(owner)) {
				sendMessage(chatId, "Anda Bukan Admin", telegramClient);
				return;
			}
			List<User> user = userService.findAllUsers();
			processFileAndNotifyUsers(url, user, telegramClient);

		});
	}

	private void processFileAndNotifyUsers(String fileUrl, List<User> allUsers, TelegramClient telegramClient) {
		String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

		boolean success = downloadAndProcessFile(fileUrl, fileName);
		// Format current date and time
		String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));

		String resultMessage = success
			? String.format("✅ *Update berhasil: Data Simulasi diperbarui pada %s*", currentDateTime)
			: "⚠ *Gagal update. Data Pelunasan, Akan dicoba ulang.*";

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
			long DELAY_BETWEEN_USERS_MS = 100;
			Thread.sleep(DELAY_BETWEEN_USERS_MS);
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
				simulasiService.deleteAll();
				simulasiService.parseCsv(outputPath);
			}
			return true;
		} catch (Exception e) {
			log.error("❌ Gagal memproses file dari URL: {}", fileUrl, e);
			return false;
		}
	}
}
