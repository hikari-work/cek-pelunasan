package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.event.DatabaseUpdateEvent;
import org.cekpelunasan.core.event.EventType;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.platform.telegram.service.UploadProgressService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

/**
 * Handler untuk perintah {@code /uploadtagihan} — memperbarui data tagihan harian dari file CSV.
 *
 * <p>Admin cukup mengirim perintah diikuti URL file CSV yang sudah disiapkan, misalnya:
 * {@code /uploadtagihan https://contoh.com/tagihan.csv}. Bot akan mengunduh file tersebut,
 * memparsing isinya, dan menyimpan seluruh data ke database.</p>
 *
 * <p>Setelah proses selesai (berhasil atau gagal), bot menerbitkan sebuah {@link DatabaseUpdateEvent}
 * agar komponen lain yang perlu tahu tentang perubahan data tagihan bisa bereaksi secara otomatis.</p>
 *
 * <p>Hanya admin yang dapat menjalankan perintah ini.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillsUpdateDataHandler extends AbstractCommandHandler {

    private static final String PROCESSING_MESSAGE = "⏳ *Sedang mengunduh dan memproses file...*";
    private static final String ERROR_DOWNLOAD = "❌ Gagal memproses file";

    private final BillService billService;
    private final ApplicationEventPublisher publisher;
    private final UploadProgressService progressService;

    /**
     * Memeriksa bahwa pengirim adalah admin sebelum melanjutkan proses upload.
     *
     * @param update objek update dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return hasil proses upload, atau ditolak jika bukan admin
     */
    @Override
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public String getCommand() {
        return "/uploadtagihan";
    }

    @Override
    public String getDescription() {
        return "Gunakan Command ini untuk upload data tagihan harian.";
    }

    /**
     * Mengunduh file CSV dari URL yang disertakan, lalu memparsing dan menyimpannya ke database.
     *
     * <p>Alur kerjanya:</p>
     * <ol>
     *   <li>Ekstrak URL dari teks perintah. Jika tidak ada URL, proses dihentikan.</li>
     *   <li>Kirim notifikasi ke admin bahwa proses sedang berjalan.</li>
     *   <li>Unduh file CSV secara asynchronous.</li>
     *   <li>Parse dan simpan data ke database melalui {@link BillService}.</li>
     *   <li>Terbitkan {@link DatabaseUpdateEvent} untuk memberitahu komponen lain bahwa data sudah diperbarui.</li>
     * </ol>
     *
     * @param chatId ID chat admin yang mengirim perintah
     * @param text   teks perintah lengkap yang berisi URL file CSV
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah data berhasil disimpan atau error ditangani
     */
    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String fileUrl = CsvDownloadUtils.extractUrl(text);
        if (fileUrl == null) {
            log.warn("Invalid format from chat {}", chatId);
            return Mono.empty();
        }
        log.info("Command: /uploadtagihan Executed with file: {}", CsvDownloadUtils.extractFileName(fileUrl));
        return Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
            .flatMap(filePath -> {
                long total = progressService.countLines(filePath);
                long[] msgIdRef = {progressService.sendProgressMessage(chatId, "Data Tagihan", total, client)};
                return billService.parseCsvAndSaveIntoDatabase(filePath, total,
                    done -> progressService.updateProgress(chatId, msgIdRef[0], "Data Tagihan", done, total, client));
            })
            .doOnSuccess(v -> {
                publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, true));
                sendMessage(chatId, "✅ Data tagihan berhasil diperbarui", client);
            })
            .onErrorResume(e -> {
                log.error("Error processing CSV file: {}", fileUrl, e);
                sendMessage(chatId, ERROR_DOWNLOAD + ": " + e.getMessage(), client);
                publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, false));
                return Mono.empty();
            });
    }
}
