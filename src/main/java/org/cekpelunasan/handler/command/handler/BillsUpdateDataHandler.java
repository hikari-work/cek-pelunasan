package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.event.database.DatabaseUpdateEvent;
import org.cekpelunasan.event.database.EventType;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.Bill.BillService;
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
public class BillsUpdateDataHandler implements CommandProcessor {

	private static final String COMMAND = "/uploadtagihan";
	private static final String CSV_EXTENSION = ".csv";
	private static final String UPLOAD_DIRECTORY = "files";
	private static final String ERROR_DOWNLOAD = "❌ Gagal memproses file";
	private static final String PROCESSING_MESSAGE = "⏳ *Sedang mengunduh dan memproses file...*";

	private final BillService billService;
	private final ApplicationEventPublisher publisher;
	private final MessageTemplate messageTemplate;

	@Value("${telegram.bot.owner}")
	private String botOwner;

	@Override
	public String getCommand() {
		return COMMAND;
	}

	@Override
	public String getDescription() {
		return "Gunakan Command ini untuk upload data tagihan harian.";
	}

	@Override
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		log.info("Received command: {}", text);
		return CompletableFuture.runAsync(() -> processRequest(chatId, text, telegramClient));
	}


	private void processRequest(long chatId, String text, TelegramClient telegramClient) {
		log.info("Update Received...");

		if (!isAdmin(chatId)) {
			log.warn("Not Admin: {}", chatId);
			sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
			return;
		}

		String fileUrl = extractFileUrl(text);
		if (fileUrl == null) {
			log.warn("Invalid format from chat {}", chatId);
			return;
		}

		processAndNotify(fileUrl, chatId, telegramClient);
	}

	private boolean isAdmin(long chatId) {
		try {
			return botOwner.equalsIgnoreCase(String.valueOf(chatId));
		} catch (NumberFormatException e) {
			log.error("Invalid bot owner ID configuration: {}", botOwner, e);
			return false;
		}
	}

	private String extractFileUrl(String text) {
		try {
			String[] parts = text.split(" ", 2);
			if (parts.length < 2) {
				throw new ArrayIndexOutOfBoundsException("URL not provided");
			}
			return parts[1];
		} catch (ArrayIndexOutOfBoundsException e) {
			log.debug("Invalid command format: {}", e.getMessage());
			return null;
		}
	}

	private void processAndNotify(String fileUrl, long chatId, TelegramClient telegramClient) {
		String fileName = extractFileName(fileUrl);

		sendMessage(chatId, PROCESSING_MESSAGE, telegramClient);
		log.info("Command: /uploadtagihan Executed with file: {}", fileName);

		try {
			boolean processed = processCsvFile(fileUrl, fileName);
			publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, processed));

			if (!processed) {
				sendMessage(chatId, ERROR_DOWNLOAD, telegramClient);
			}
		} catch (Exception e) {
			log.error("Error processing CSV file: {}", fileUrl, e);
			sendMessage(chatId, ERROR_DOWNLOAD, telegramClient);
		}
	}

	private String extractFileName(String fileUrl) {
		return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
	}

	private boolean processCsvFile(String fileUrl, String fileName) throws Exception {
		if (!isCsvFile(fileName)) {
			log.warn("File is not CSV: {}", fileName);
			return false;
		}

		Path filePath = downloadFile(fileUrl, fileName);
		billService.deleteAll();
		billService.parseCsvAndSaveIntoDatabase(filePath);
		log.info("CSV file processed successfully: {}", fileName);
		return true;
	}

	private boolean isCsvFile(String fileName) {
		return fileName.endsWith(CSV_EXTENSION);
	}

	private Path downloadFile(String fileUrl, String fileName) throws Exception {
		Path filePath = Paths.get(UPLOAD_DIRECTORY, fileName);
		Files.createDirectories(filePath.getParent());

		try (InputStream input = new URL(fileUrl).openStream()) {
			Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
		}

		return filePath;
	}
}