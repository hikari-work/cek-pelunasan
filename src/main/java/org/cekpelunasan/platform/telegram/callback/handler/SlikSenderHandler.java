package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.slik.GeneratePdfFiles;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

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
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");

        if (!isValidCallbackFormat(data)) {
            log.warn("Invalid callback format received: {}", callbackData);
            return Mono.fromRunnable(() -> telegramMessageService.sendText(update.chatId, INVALID_CALLBACK_MESSAGE, client));
        }

        String customerId = data[CUSTOMER_ID_INDEX];
        Boolean isActiveFacility = data[IDENTIFIER_INDEX].equals(String.valueOf(ACTIVE_FACILITY_VALUE));
        long chatId = update.chatId;
        long messageId = update.messageId;

        telegramMessageService.delete(chatId, messageId, client);

        log.info("Processing SLIK request - Customer ID: {}, Active Facility: {}", customerId, isActiveFacility);

        long notificationId = telegramMessageService.sendText(chatId, LOADING_MESSAGE, client);
        if (notificationId == 0L) {
            log.warn("Failed to send initial notification");
            return Mono.empty();
        }

        return s3Connector.getFile(buildFileName(customerId))
            .switchIfEmpty(Mono.fromRunnable(() -> {
                log.warn("KTP file not found - ID: {}", customerId);
                telegramMessageService.editText(chatId, notificationId, String.format(FILE_NOT_FOUND_MESSAGE, customerId), client);
            }))
            .flatMap(fileContent -> {
                if (fileContent.length > maxFileSize) {
                    log.warn("File size exceeds maximum - KTP ID: {}, Size: {}", customerId, fileContent.length);
                    return Mono.fromRunnable(() ->
                        telegramMessageService.editText(chatId, notificationId, "❌ File terlalu besar untuk diproses", client));
                }
                log.debug("Generating PDF for KTP - ID: {}", customerId);
                return generatePdfFiles.generatePdf(fileContent, isActiveFacility)
                    .switchIfEmpty(Mono.fromRunnable(() -> {
                        log.warn("Failed to generate PDF - KTP ID: {}", customerId);
                        telegramMessageService.editText(chatId, notificationId, String.format(FILE_NOT_FOUND_MESSAGE, customerId), client);
                    }))
                    .flatMap(pdfBytes -> Mono.fromRunnable(() -> {
                        telegramMessageService.editText(chatId, notificationId, String.format(FILE_FOUND_MESSAGE, customerId), client);
                        telegramMessageService.delete(chatId, notificationId, client);
                        telegramMessageService.sendDocument(chatId, buildPdfFileName(customerId), pdfBytes, client);
                        log.info("Successfully processed and sent PDF - KTP ID: {}", customerId);
                    }));
            })
            .onErrorResume(e -> {
                log.error("Unexpected error processing SLIK callback", e);
                return Mono.fromRunnable(() -> telegramMessageService.sendText(chatId, ERROR_MESSAGE, client));
            })
            .then();
    }

    private boolean isValidCallbackFormat(String[] data) {
        return data != null && data.length >= CALLBACK_DATA_MIN_PARTS;
    }

    private String buildFileName(String ktpId) {
        return KTP_PREFIX + ktpId + KTP_EXTENSION;
    }

    private String buildPdfFileName(String ktpId) {
        return ktpId + ".pdf";
    }
}
