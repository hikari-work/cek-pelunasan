package org.cekpelunasan.handler.callback.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.slik.GeneratePdfFiles;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.service.telegram.TelegramMessageService;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Component
public class SlikSenderHandler implements CallbackProcessor {

	private static final String KTP_PREFIX = "KTP_";
	private static final String KTP_EXTENSION = ".txt";
 private static final String CALLBACK_PATTERN = "slik";
	private static final int CALLBACK_DATA_MIN_PARTS = 3;
	private static final int CUSTOMER_ID_INDEX = 1;
	private static final int IDENTIFIER_INDEX = 2;
	private static final int ACTIVE_FACILITY_VALUE = 1;

	private static final String LOADING_MESSAGE = "⏳ Mengambil Data KTP...";
	private static final String FILE_NOT_FOUND_MESSAGE = "❌ Data KTP `%s` tidak ditemukan";
	private static final String FILE_FOUND_MESSAGE = "✅ Data KTP `%s` ditemukan. Menggenerate PDF...";
	private static final String ERROR_MESSAGE = "⚠️ Terjadi kesalahan saat memproses data. Silakan coba lagi.";
	private static final String INVALID_CALLBACK_MESSAGE = "⚠️ Format callback tidak valid";

	@Value("${slik.pdf.max-size:5242880000}")
	private long maxFileSize;


    private final S3ClientConfiguration s3Connector;
    private final GeneratePdfFiles generatePdfFiles;
    private final TelegramMessageService telegramMessageService;

	@Override
	public String getCallBackData() {
		return CALLBACK_PATTERN;
	}

	@Override
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			try {
				String callbackData = update.getCallbackQuery().getData();
				String[] data = callbackData.split("_");

    if (!isValidCallbackFormat(data)) {
                    log.warn("Invalid callback format received: {}", callbackData);
                    telegramMessageService.sendText(update.getCallbackQuery().getMessage().getChatId(),
                        INVALID_CALLBACK_MESSAGE, telegramClient);
                    return;
                }
                telegramMessageService.delete(update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), telegramClient);

				String customerId = data[CUSTOMER_ID_INDEX];
				Boolean isActiveFacility = data[IDENTIFIER_INDEX].equals(String.valueOf(ACTIVE_FACILITY_VALUE));
				long chatId = update.getCallbackQuery().getMessage().getChatId();

				log.info("Processing SLIK request - Customer ID: {}, Active Facility: {}",
					customerId, isActiveFacility);

				processSlikSearchById(customerId, chatId, telegramClient, isActiveFacility);

            } catch (ArrayIndexOutOfBoundsException e) {
                log.error("Invalid callback data structure", e);
                telegramMessageService.sendText(update.getCallbackQuery().getMessage().getChatId(),
                    INVALID_CALLBACK_MESSAGE, telegramClient);
            } catch (Exception e) {
                log.error("Unexpected error processing callback", e);
                telegramMessageService.sendText(update.getCallbackQuery().getMessage().getChatId(),
                    ERROR_MESSAGE, telegramClient);
            }
        });
    }

	private boolean isValidCallbackFormat(String[] data) {
		return data != null && data.length >= CALLBACK_DATA_MIN_PARTS;
	}

	private void processSlikSearchById(String ktpId, long chatId, TelegramClient telegramClient,
									   Boolean isActiveFacility) {
		try {
   Message notification = telegramMessageService.sendText(chatId, LOADING_MESSAGE, telegramClient);
   if (notification == null) {
       log.warn("Failed to send initial notification");
       return;
   }

			log.debug("Fetching KTP file from S3 - ID: {}", ktpId);
			byte[] fileContent = s3Connector.getFile(buildFileName(ktpId));

   if (fileContent == null || fileContent.length == 0) {
                log.warn("KTP file not found - ID: {}", ktpId);
                telegramMessageService.editText(chatId, notification.getMessageId(),
                    String.format(FILE_NOT_FOUND_MESSAGE, ktpId), telegramClient);
                return;
            }

			processKtpFile(ktpId, fileContent, chatId, notification.getMessageId(),
				telegramClient, isActiveFacility);

        } catch (Exception e) {
            log.error("Unexpected error in processSlikSearchById", e);
            telegramMessageService.sendText(chatId, ERROR_MESSAGE, telegramClient);
        }
    }

	private void processKtpFile(String ktpId, byte[] fileContent, long chatId, int messageId,
								TelegramClient telegramClient, Boolean isActiveFacility) {
		try {
   if (fileContent.length > maxFileSize) {
                log.warn("File size exceeds maximum allowed size - KTP ID: {}, Size: {}",
                    ktpId, fileContent.length);
                telegramMessageService.editText(chatId, messageId, "❌ File terlalu besar untuk diproses", telegramClient);
                return;
            }

			log.debug("Generating HTML content for KTP - ID: {}", ktpId);
			String htmlContent = generatePdfFiles.generateHtmlContent(fileContent, isActiveFacility);

   if (htmlContent == null || htmlContent.trim().isEmpty()) {
                log.warn("Failed to generate HTML content - KTP ID: {}", ktpId);
                telegramMessageService.editText(chatId, messageId,
                    String.format(FILE_NOT_FOUND_MESSAGE, ktpId), telegramClient);
                return;
            }

			log.debug("Parsing HTML and generating PDF - KTP ID: {}", ktpId);
			Document document = generatePdfFiles.parsingHtmlContentAndManipulatePages(htmlContent);

   if (document == null) {
                log.warn("Failed to parse HTML document - KTP ID: {}", ktpId);
                telegramMessageService.editText(chatId, messageId, ERROR_MESSAGE, telegramClient);
                return;
            }

			byte[] pdfBytes = generatePdfFiles.generatePdfBytes(document);

   if (pdfBytes == null || pdfBytes.length == 0) {
                log.warn("Failed to generate PDF bytes - KTP ID: {}", ktpId);
                telegramMessageService.editText(chatId, messageId, ERROR_MESSAGE, telegramClient);
                return;
            }

   telegramMessageService.editText(chatId, messageId,
                String.format(FILE_FOUND_MESSAGE, ktpId), telegramClient);
            telegramMessageService.delete(chatId, messageId, telegramClient);
            telegramMessageService.sendDocument(chatId, buildPdfFileName(ktpId), pdfBytes, telegramClient);

			log.info("Successfully processed and sent PDF - KTP ID: {}", ktpId);
        } catch (Exception e) {
            log.error("Unexpected error in processKtpFile", e);
            telegramMessageService.editText(chatId, messageId, ERROR_MESSAGE, telegramClient);
        }
    }

	private String buildFileName(String ktpId) {
		return KTP_PREFIX + ktpId + KTP_EXTENSION;
	}

	private String buildPdfFileName(String ktpId) {
		return ktpId + ".pdf";
	}
}