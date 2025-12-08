package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.event.database.DatabaseUpdateEvent;
import org.cekpelunasan.event.database.EventType;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.kolektas.KolekTasService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadTasHandler implements CommandProcessor {

	private static final String COMMAND = "/uploadtas";
	private static final String CSV_EXTENSION = ".csv";
	private static final String UPLOAD_DIRECTORY = "files";
	private static final String ERROR_UNAUTHORIZED = "Unauthorized";
	private static final String ERROR_FORMAT = "❗ *Format salah.*\nGunakan `/uploadtas <link_csv>`";
	private static final String ERROR_DOWNLOAD = "❌ Gagal memproses file dari URL: {}";

	private final ApplicationEventPublisher publisher;
	private final KolekTasService kolekTasService;

	@Value("${telegram.bot.owner}")
	private String ownerId;

	@Override
	public String getCommand() {
		return COMMAND;
	}

	@Override
	public String getDescription() {
		return "Upload dan proses file CSV untuk update data koleksi tas";
	}

	@Override
	@RequireAuth(roles = AccountOfficerRoles.ADMIN)
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> processRequest(chatId, text, telegramClient));
	}

	private void processRequest(long chatId, String text, TelegramClient telegramClient) {
		if (isOnlyCommand(text)) {
			sendMessage(chatId, "Gunakan format: /uploadtas <link_csv>", telegramClient);
			return;
		}

		if (!isAdmin(chatId)) {
			sendMessage(chatId, ERROR_UNAUTHORIZED, telegramClient);
			return;
		}

		String fileUrl = extractFileUrl(text, chatId, telegramClient);
		if (fileUrl != null) {
			processFileAndNotifyUsers(fileUrl);
		}
	}

	private boolean isOnlyCommand(String text) {
		return COMMAND.equals(text);
	}

	private boolean isAdmin(long chatId) {
		try {
			return Long.parseLong(ownerId) == chatId;
		} catch (NumberFormatException e) {
			log.error("Invalid owner ID configuration: {}", ownerId, e);
			return false;
		}
	}

	private String extractFileUrl(String text, long chatId, TelegramClient telegramClient) {
		try {
			String[] parts = text.split(" ", 2);
			if (parts.length < 2) {
				throw new ArrayIndexOutOfBoundsException();
			}
			return parts[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			sendMessage(chatId, ERROR_FORMAT, telegramClient);
			log.debug("Invalid URL format from chat {}", chatId);
			return null;
		}
	}

	private void processFileAndNotifyUsers(String fileUrl) {
		String fileName = extractFileName(fileUrl);
		boolean success = downloadAndProcessFile(fileUrl, fileName);
		publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.KOLEK_TAS, success));
	}

	private String extractFileName(String fileUrl) {
		return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
	}

	private boolean downloadAndProcessFile(String fileUrl, String fileName) {
		try {
			Path outputPath = downloadFile(fileUrl, fileName);
			if (isCsvFile(fileName)) {
				processCSVFile(outputPath);
			}
			log.info("File berhasil diproses: {}", fileName);
			return true;
		} catch (Exception e) {
			log.error(ERROR_DOWNLOAD, fileUrl, e);
			return false;
		}
	}

	private Path downloadFile(String fileUrl, String fileName) throws Exception {
		Path outputPath = Paths.get(UPLOAD_DIRECTORY, fileName);
		Files.createDirectories(outputPath.getParent());

		try (InputStream inputStream = new URL(fileUrl).openStream()) {
			Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
		}

		return outputPath;
	}

	private boolean isCsvFile(String fileName) {
		return fileName.endsWith(CSV_EXTENSION);
	}

	private void processCSVFile(Path filePath) {
		kolekTasService.deleteAll();
		kolekTasService.parseCsvAndSave(filePath);
		log.info("CSV file processed successfully: {}", filePath.getFileName());
	}

}