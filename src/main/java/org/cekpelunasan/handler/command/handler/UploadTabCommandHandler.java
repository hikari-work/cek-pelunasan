package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.event.database.DatabaseUpdateEvent;
import org.cekpelunasan.event.database.EventType;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.savings.SavingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
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
public class UploadTabCommandHandler implements CommandProcessor {

	private static final String COMMAND = "/uploadtab";
	private static final String CSV_EXTENSION = ".csv";
	private static final String UPLOAD_DIRECTORY = "files";
	private static final String ERROR_UNAUTHORIZED = "Unauthorized";
	private static final String ERROR_FORMAT = "❗ *Format salah.*\nGunakan `/upload <link_csv>`";

	private final ApplicationEventPublisher publisher;
	private final SavingsService savingsService;

	@Value("${telegram.bot.owner}")
	private String botOwner;

	@Override
	public String getCommand() {
		return COMMAND;
	}

	@Override
	public String getDescription() {
		return "Upload dan proses file CSV untuk update data tabungan";
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> processUpload(chatId, text, telegramClient));
	}

	private void processUpload(long chatId, String text, TelegramClient telegramClient) {
		if (!isAdmin(chatId)) {
			sendMessage(chatId, ERROR_UNAUTHORIZED, telegramClient);
			return;
		}

		String fileUrl = extractFileUrl(text, chatId, telegramClient);
		if (fileUrl != null) {
			processFile(fileUrl);
		}
	}

	private boolean isAdmin(long chatId) {
		return botOwner.equals(String.valueOf(chatId));
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
			log.debug("Format URL tidak valid dari chat {}", chatId);
			return null;
		}
	}

	private void processFile(String fileUrl) {
		String fileName = extractFileName(fileUrl);
		boolean success = downloadAndProcessFile(fileUrl, fileName);
		publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.SAVING, success));
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
			return true;
		} catch (Exception e) {
			log.error("Gagal memproses file dari URL: {}", fileUrl, e);
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
		savingsService.deleteAll();
		savingsService.parseCsvAndSaveIntoDatabase(filePath);
		log.info("File CSV berhasil diproses: {}", filePath.getFileName());
	}

}