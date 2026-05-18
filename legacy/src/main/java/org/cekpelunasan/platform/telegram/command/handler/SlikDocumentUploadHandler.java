package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.service.slik.MonthFolderProvider;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Menerima dokumen yang dikirim Admin ke bot Telegram dan meng-upload-nya ke R2.
 *
 * <p>Subfolder di R2 ditentukan otomatis dari extension file:</p>
 * <ul>
 *   <li>{@code .pdf}  → {@code {folder}/pdf/filename.pdf}</li>
 *   <li>{@code .txt}  → {@code {folder}/txt/filename.txt}</li>
 *   <li>{@code .ideb} → {@code {folder}/ideb/filename.ideb}</li>
 * </ul>
 *
 * <p>Folder mengikuti bulan saat ini, misal {@code 2026_05}. Hanya user dengan
 * role {@link AccountOfficerRoles#ADMIN} yang dapat menggunakan fitur ini.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlikDocumentUploadHandler {

    private static final Map<String, String> CONTENT_TYPE_MAP = Map.of(
        "pdf",  "application/pdf",
        "txt",  "text/plain",
        "ideb", "application/octet-stream"
    );

    private final UserService userService;
    private final S3ClientConfiguration s3Config;
    private final MonthFolderProvider monthFolderProvider;
    private final TelegramMessageService telegramMessageService;

    /**
     * Menangani pesan yang berisi dokumen dari Telegram.
     * Hanya Admin yang diijinkan; pesan dari role lain diabaikan.
     *
     * @param update update pesan masuk yang berisi MessageDocument
     * @param client koneksi TDLight aktif
     */
    @Async
    public void handle(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        if (!(update.message.content instanceof TdApi.MessageDocument msgDoc)) {
            return;
        }

        long chatId = update.message.chatId;
        TdApi.Document document = msgDoc.document;
        String fileName = document.fileName;

        userService.findUserByChatId(chatId)
            .filter(user -> user.getRoles() == AccountOfficerRoles.ADMIN)
            .subscribe(
                user -> downloadAndUpload(chatId, document, fileName, client),
                e -> log.error("Error checking user role for SLIK upload, chatId={}: {}", chatId, e.getMessage())
            );
    }

    private void downloadAndUpload(long chatId, TdApi.Document document, String fileName, SimpleTelegramClient client) {
        String ext = getExtension(fileName);
        String subfolder = getSubfolder(ext);

        if (subfolder == null) {
            telegramMessageService.sendText(chatId,
                "⚠️ Extension `" + ext + "` tidak didukung. Gunakan: pdf, txt, ideb", client);
            return;
        }

        String r2Key = monthFolderProvider.currentFolder() + "/" + subfolder + "/" + fileName;

        try {
            TdApi.DownloadFile downloadReq = new TdApi.DownloadFile();
            downloadReq.fileId = document.document.id;
            downloadReq.priority = 32;
            downloadReq.synchronous = true;

            CompletableFuture<byte[]> future = new CompletableFuture<>();
            client.send(downloadReq, result -> {
                if (result.isError()) {
                    log.error("Failed to download Telegram file {} for chatId={}: {}", fileName, chatId, result.getError().message);
                    future.completeExceptionally(new RuntimeException(result.getError().message));
                    return;
                }
                String localPath = result.get().local.path;
                try {
                    future.complete(Files.readAllBytes(Path.of(localPath)));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            byte[] bytes = future.get(60, TimeUnit.SECONDS);
            String contentType = CONTENT_TYPE_MAP.getOrDefault(ext, "application/octet-stream");

            s3Config.putObject(r2Key, bytes, contentType)
                .doOnSuccess(v -> {
                    log.info("SLIK upload success: {} ({} bytes) by chatId={}", r2Key, bytes.length, chatId);
                    telegramMessageService.sendText(chatId,
                        "✅ Upload berhasil: `" + r2Key + "`", client);
                })
                .doOnError(e -> {
                    log.error("SLIK upload to R2 failed for key={}: {}", r2Key, e.getMessage());
                    telegramMessageService.sendText(chatId, "❌ Gagal upload ke R2: " + e.getMessage(), client);
                })
                .subscribe();

        } catch (Exception e) {
            log.error("SLIK upload error for chatId={}, file={}: {}", chatId, fileName, e.getMessage());
            telegramMessageService.sendText(chatId, "❌ Gagal memproses file: " + e.getMessage(), client);
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String getSubfolder(String ext) {
        return switch (ext) {
            case "pdf"  -> "pdf";
            case "txt"  -> "txt";
            case "ideb" -> "ideb";
            default     -> null;
        };
    }
}
