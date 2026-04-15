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

/**
 * Handler untuk mengambil data KTP dari S3 dan menghasilkan dokumen PDF SLIK.
 *
 * <p>Callback berawalan {@code "slik"} ini menangani permintaan generate PDF
 * laporan SLIK. Alurnya: cari file KTP di S3 berdasarkan ID nasabah → generate
 * PDF dari konten file tersebut → kirim PDF sebagai dokumen ke chat user.
 *
 * <p>Selama proses berlangsung, pesan loading ditampilkan dan diperbarui sesuai
 * status terkini, sehingga user tahu prosesnya sedang berjalan.
 *
 * <p>Format data callback yang diharapkan:
 * {@code "slik_<customer_id>_<is_active_facility>"}, di mana
 * {@code is_active_facility} bernilai {@code 1} untuk fasilitas aktif.
 */
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

    /**
     * Mengembalikan prefix {@code "slik"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return CALLBACK_PATTERN;
    }

    /**
     * Memproses permintaan generate PDF SLIK dari data KTP yang tersimpan di S3.
     *
     * <p>Alur lengkapnya:
     * <ol>
     *   <li>Validasi format data callback</li>
     *   <li>Hapus pesan trigger (tombol yang ditekan user)</li>
     *   <li>Kirim pesan loading sebagai placeholder</li>
     *   <li>Ambil file KTP dari S3 berdasarkan ID nasabah</li>
     *   <li>Periksa ukuran file tidak melebihi batas maksimum</li>
     *   <li>Generate PDF dari konten file KTP</li>
     *   <li>Kirim PDF sebagai dokumen ke chat user</li>
     * </ol>
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah PDF berhasil dikirim atau error ditangani
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");

        if (!isValidCallbackFormat(data)) {
            log.warn("Invalid callback format received: {}", callbackData);
            return runBlocking(() -> telegramMessageService.sendText(update.chatId, INVALID_CALLBACK_MESSAGE, client));
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
            .switchIfEmpty(runBlocking(() -> {
                log.warn("KTP file not found - ID: {}", customerId);
                telegramMessageService.editText(chatId, notificationId, String.format(FILE_NOT_FOUND_MESSAGE, customerId), client);
            }))
            .flatMap(fileContent -> {
                if (fileContent.length > maxFileSize) {
                    log.warn("File size exceeds maximum - KTP ID: {}, Size: {}", customerId, fileContent.length);
                    return runBlocking(() ->
                        telegramMessageService.editText(chatId, notificationId, "❌ File terlalu besar untuk diproses", client));
                }
                log.debug("Generating PDF for KTP - ID: {}", customerId);
                return generatePdfFiles.generatePdf(fileContent, isActiveFacility)
                    .switchIfEmpty(runBlocking(() -> {
                        log.warn("Failed to generate PDF - KTP ID: {}", customerId);
                        telegramMessageService.editText(chatId, notificationId, String.format(FILE_NOT_FOUND_MESSAGE, customerId), client);
                    }))
                    .flatMap(pdfBytes -> runBlocking(() -> {
                        telegramMessageService.editText(chatId, notificationId, String.format(FILE_FOUND_MESSAGE, customerId), client);
                        telegramMessageService.delete(chatId, notificationId, client);
                        telegramMessageService.sendDocument(chatId, buildPdfFileName(customerId), pdfBytes, client);
                        log.info("Successfully processed and sent PDF - KTP ID: {}", customerId);
                    }));
            })
            .onErrorResume(e -> {
                log.error("Unexpected error processing SLIK callback", e);
                return runBlocking(() -> telegramMessageService.sendText(chatId, ERROR_MESSAGE, client));
            })
            .then();
    }

    /**
     * Memvalidasi bahwa array data callback memiliki jumlah bagian yang cukup.
     *
     * @param data hasil split dari string data callback
     * @return {@code true} jika jumlah bagian minimal sesuai kebutuhan
     */
    private boolean isValidCallbackFormat(String[] data) {
        return data != null && data.length >= CALLBACK_DATA_MIN_PARTS;
    }

    /**
     * Membangun nama file KTP di S3 dari ID nasabah.
     *
     * <p>Contoh: ID {@code "1234567890"} menjadi {@code "KTP_1234567890.txt"}.
     *
     * @param ktpId ID nasabah / nomor KTP
     * @return nama file lengkap termasuk prefix dan ekstensi
     */
    private String buildFileName(String ktpId) {
        return KTP_PREFIX + ktpId + KTP_EXTENSION;
    }

    /**
     * Membangun nama file PDF yang akan dikirim ke user.
     *
     * <p>Contoh: ID {@code "1234567890"} menjadi {@code "1234567890.pdf"}.
     *
     * @param ktpId ID nasabah / nomor KTP
     * @return nama file PDF tanpa path
     */
    private String buildPdfFileName(String ktpId) {
        return ktpId + ".pdf";
    }
}
