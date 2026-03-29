package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.slik.GeneratePdfFiles;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Component
public class SlikSenderHandler extends AbstractCallbackHandler {

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

    @Override
    public String getCallBackData() {
        return CALLBACK_PATTERN;
    }

    @Override
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            try {
                String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
                String[] data = callbackData.split("_");

                if (!isValidCallbackFormat(data)) {
                    log.warn("Invalid callback format received: {}", callbackData);
                    telegramMessageService.sendText(update.chatId, INVALID_CALLBACK_MESSAGE, client);
                    return;
                }
                telegramMessageService.delete(update.chatId, update.messageId, client);

                String customerId = data[CUSTOMER_ID_INDEX];
                Boolean isActiveFacility = data[IDENTIFIER_INDEX].equals(String.valueOf(ACTIVE_FACILITY_VALUE));
                long chatId = update.chatId;

                log.info("Processing SLIK request - Customer ID: {}, Active Facility: {}",
                    customerId, isActiveFacility);

                processSlikSearchById(customerId, chatId, client, isActiveFacility);

            } catch (ArrayIndexOutOfBoundsException e) {
                log.error("Invalid callback data structure", e);
                telegramMessageService.sendText(update.chatId, INVALID_CALLBACK_MESSAGE, client);
            } catch (Exception e) {
                log.error("Unexpected error processing callback", e);
                telegramMessageService.sendText(update.chatId, ERROR_MESSAGE, client);
            }
        });
    }

    private boolean isValidCallbackFormat(String[] data) {
        return data != null && data.length >= CALLBACK_DATA_MIN_PARTS;
    }

    private void processSlikSearchById(String ktpId, long chatId, SimpleTelegramClient client,
                                       Boolean isActiveFacility) {
        try {
            long notificationId = telegramMessageService.sendText(chatId, LOADING_MESSAGE, client);
            if (notificationId == 0L) {
                log.warn("Failed to send initial notification");
                return;
            }

            log.debug("Fetching KTP file from S3 - ID: {}", ktpId);
            byte[] fileContent = s3Connector.getFile(buildFileName(ktpId));

            if (fileContent == null || fileContent.length == 0) {
                log.warn("KTP file not found - ID: {}", ktpId);
                telegramMessageService.editText(chatId, notificationId,
                    String.format(FILE_NOT_FOUND_MESSAGE, ktpId), client);
                return;
            }

            processKtpFile(ktpId, fileContent, chatId, notificationId, client, isActiveFacility);

        } catch (Exception e) {
            log.error("Unexpected error in processSlikSearchById", e);
            telegramMessageService.sendText(chatId, ERROR_MESSAGE, client);
        }
    }

    private void processKtpFile(String ktpId, byte[] fileContent, long chatId, long messageId,
                                 SimpleTelegramClient client, Boolean isActiveFacility) {
        try {
            if (fileContent.length > maxFileSize) {
                log.warn("File size exceeds maximum allowed size - KTP ID: {}, Size: {}",
                    ktpId, fileContent.length);
                telegramMessageService.editText(chatId, messageId, "❌ File terlalu besar untuk diproses", client);
                return;
            }

            log.debug("Generating HTML content for KTP - ID: {}", ktpId);
            String htmlContent = generatePdfFiles.generateHtmlContent(fileContent, isActiveFacility);

            if (htmlContent == null || htmlContent.trim().isEmpty()) {
                log.warn("Failed to generate HTML content - KTP ID: {}", ktpId);
                telegramMessageService.editText(chatId, messageId,
                    String.format(FILE_NOT_FOUND_MESSAGE, ktpId), client);
                return;
            }

            log.debug("Parsing HTML and generating PDF - KTP ID: {}", ktpId);
            Document document = generatePdfFiles.parsingHtmlContentAndManipulatePages(htmlContent);

            if (document == null) {
                log.warn("Failed to parse HTML document - KTP ID: {}", ktpId);
                telegramMessageService.editText(chatId, messageId, ERROR_MESSAGE, client);
                return;
            }

            byte[] pdfBytes = generatePdfFiles.generatePdfBytes(document);

            if (pdfBytes == null || pdfBytes.length == 0) {
                log.warn("Failed to generate PDF bytes - KTP ID: {}", ktpId);
                telegramMessageService.editText(chatId, messageId, ERROR_MESSAGE, client);
                return;
            }

            telegramMessageService.editText(chatId, messageId,
                String.format(FILE_FOUND_MESSAGE, ktpId), client);
            telegramMessageService.delete(chatId, messageId, client);
            telegramMessageService.sendDocument(chatId, buildPdfFileName(ktpId), pdfBytes, client);

            log.info("Successfully processed and sent PDF - KTP ID: {}", ktpId);
        } catch (Exception e) {
            log.error("Unexpected error in processKtpFile", e);
            telegramMessageService.editText(chatId, messageId, ERROR_MESSAGE, client);
        }
    }

    private String buildFileName(String ktpId) {
        return KTP_PREFIX + ktpId + KTP_EXTENSION;
    }

    private String buildPdfFileName(String ktpId) {
        return ktpId + ".pdf";
    }
}
