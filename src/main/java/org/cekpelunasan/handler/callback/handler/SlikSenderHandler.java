package org.cekpelunasan.handler.callback.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.slik.GeneratePDF;
import org.cekpelunasan.service.slik.S3Connector;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Component
public class SlikSenderHandler implements CallbackProcessor {

	private static final String KTP_PREFIX = "KTP_";
	private static final String KTP_EXTENSION = ".txt";
	private static final String MARKDOWN_PARSE_MODE = "Markdown";
	private static final String LOADING_MESSAGE = "Mengambil Data KTP";
	private static final String FILE_NOT_FOUND_FORMAT = "Data KTP `%s` tidak ada";
	private static final String FILE_FOUND_FORMAT = "Data KTP `%s` Ditemukan....";

	private final S3Connector s3Connector;
	private final GeneratePDF generatePDF;

	@Override
	public String getCallBackData() {
		return "slik";
	}

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String[] data = update.getCallbackQuery().getData().split("_");
			String customerId = data[1];
			String identifier = data[2];
			log.info("Searching KTP ID: {}", customerId);
			processSlikSearchById(customerId, update.getCallbackQuery().getMessage().getChatId(), telegramClient, identifier.equals("1"));

		});
	}
	private void processSlikSearchById(String ktpId, long chatId, TelegramClient telegramClient, Boolean fasilitasAktif) {
		Message notification = sendNotification(chatId, telegramClient);
		log.info("Sending Notification....");
		log.info("Fasilitas Aktif is {}", fasilitasAktif ? "true" : "false");
		if (notification == null) {
			return;
		}

		String filename = KTP_PREFIX + ktpId + KTP_EXTENSION;
		log.info("Fetching KTP ID: {} from S3", ktpId);
		byte[] files = s3Connector.getFile(filename);

		if (files == null) {
			log.info("File not found for KTP ID: {}", ktpId);
			editMessage(chatId, notification.getMessageId(),
				String.format(FILE_NOT_FOUND_FORMAT, ktpId), telegramClient);
			return;
		}

		processKtpFile(ktpId, files, chatId, notification.getMessageId(), telegramClient, fasilitasAktif);
	}
	private void processKtpFile(String ktpId, byte[] files, long chatId, int messageId, TelegramClient telegramClient, Boolean fasilitasAktif) {
		log.info("Generating PDF for KTP ID: {}", ktpId);
		String htmlContent = generatePDF.sendBytesWithRestTemplate(files, ktpId + KTP_EXTENSION, fasilitasAktif);
		if (htmlContent == null || htmlContent.isEmpty()) {
			log.info("Failed to generate HTML content for KTP ID: {}", ktpId);
			editMessage(chatId, messageId, String.format(FILE_NOT_FOUND_FORMAT, ktpId), telegramClient);
			return;
		}

		byte[] pdfBytes = generatePDF.convertHtmlToPdf(htmlContent);
		editMessage(chatId, messageId, String.format(FILE_FOUND_FORMAT, ktpId), telegramClient);

		if (pdfBytes == null || pdfBytes.length == 0) {
			log.info("Failed to generate PDF for KTP ID: {}", ktpId);
			sendMessage(chatId, "File not found: " + ktpId, telegramClient);
			return;
		}

		log.info("Sending PDF file for KTP ID: {}", ktpId);
		InputFile pdfFile = new InputFile(new ByteArrayInputStream(pdfBytes), ktpId + ".pdf");
		sendDocument(chatId, ktpId, pdfFile, telegramClient);
	}
	private void editMessage(Long chatId, Integer messageId, String text, TelegramClient telegramClient) {
		try {
			telegramClient.execute(EditMessageText.builder()
				.chatId(chatId)
				.text(text)
				.messageId(messageId)
				.parseMode(MARKDOWN_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.info("Error editing message: {}", e.getMessage());
		}
	}
	private Message sendNotification(Long chatId, TelegramClient telegramClient) {
		try {
			return telegramClient.execute(SendMessage.builder()
				.chatId(chatId)
				.text(LOADING_MESSAGE)
				.parseMode(MARKDOWN_PARSE_MODE)
				.build());
		} catch (Exception e) {
			log.info("Error sending notification: {}", e.getMessage());
			return null;
		}
	}

}
