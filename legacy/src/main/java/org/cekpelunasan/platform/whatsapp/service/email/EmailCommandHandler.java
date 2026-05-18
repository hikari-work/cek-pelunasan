package org.cekpelunasan.platform.whatsapp.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.dto.webhook.MediaPayloadDTO;
import org.cekpelunasan.platform.whatsapp.dto.webhook.WhatsAppWebhookDTO;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Menangani lifecycle sesi email WhatsApp:
 * <ul>
 *   <li>{@code .email} — membuka sesi baru dan mulai mengumpulkan media</li>
 *   <li>{@code .email} sebagai reply ke pesan media — langsung kirim tanpa buka sesi</li>
 *   <li>Media masuk saat sesi aktif — ditambahkan ke antrian (termasuk media group/album)</li>
 *   <li>{@code .done} — menutup sesi dan langsung mengirim email</li>
 *   <li>TTL 60 detik habis — email dikirim otomatis oleh {@link EmailSessionCache}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCommandHandler {

    private static final String EMAIL_REGEX = "^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$";

    private final EmailSessionCache sessionCache;
    private final EmailForwardService emailForwardService;
    private final WhatsAppSenderService whatsAppSenderService;

    @Value("${email.forward.recipient}")
    private String defaultRecipient;

    /**
     * Titik masuk utama untuk command .email.
     * <p>
     * Jika pesan merupakan reply ke pesan lain (ada replied_to_id), maka media dari
     * pesan yang di-reply akan langsung diunduh via gateway dan dikirim ke email tanpa
     * membuka sesi. Jika bukan reply, sesi baru dibuka untuk mengumpulkan media.
     * </p>
     * Format: ".email" → pakai recipient default dari config
     *         ".email user@example.com" → kirim ke email tersebut
     */
    public void handleStart(WhatsAppWebhookDTO webhook) {
        String phone = webhook.getCleanSenderId();
        String chatId = webhook.buildChatId();
        String fromName = webhook.getPayload().getFromName();
        String body = webhook.getPayload().getBody().trim();
        String repliedToId = webhook.getPayload().getRepliedToId();

        String recipient = resolveRecipient(body);
        if (recipient == null) {
            whatsAppSenderService.sendWhatsAppText(chatId,
                "❌ Format email tidak valid. Gunakan: *.email* atau *.email user@example.com*"
            ).subscribe();
            return;
        }

        // Reply flow: langsung kirim media dari pesan yang di-reply
        if (repliedToId != null && !repliedToId.isBlank()) {
            handleReplyForward(webhook, phone, chatId, fromName, recipient, repliedToId);
            return;
        }

        // Normal flow: buka sesi
        EmailSession session = new EmailSession(chatId, phone, fromName != null ? fromName : phone, recipient);
        sessionCache.put(session, emailForwardService::send);

        whatsAppSenderService.sendWhatsAppText(chatId,
            "📧 Sesi email dibuka. Tujuan: " + recipient + "\n" +
            "Kirimkan foto, video, atau dokumen yang ingin diteruskan.\n" +
            "Ketik *.done* jika sudah selesai, atau tunggu 60 detik untuk dikirim otomatis."
        ).subscribe();

        log.info("Email session started for {} → {}", phone, recipient);
    }

    /**
     * Mengunduh media dari pesan yang di-reply via gateway API, lalu langsung mengirim email.
     * Tidak membuka sesi — prosesnya sekali jalan tanpa interaksi tambahan dari user.
     */
    private void handleReplyForward(WhatsAppWebhookDTO webhook, String phone, String chatId,
                                    String fromName, String recipient, String repliedToId) {
        log.info("Reply-forward: downloading messageId={} for {}", repliedToId, phone);

        whatsAppSenderService.sendWhatsAppText(chatId, "⏳ Sedang memproses media...").subscribe();

        EmailSession.CollectedMedia media = emailForwardService.downloadByMessageId(repliedToId, phone);
        if (media == null) {
            whatsAppSenderService.sendWhatsAppText(chatId,
                "❌ Media tidak ditemukan atau gagal diunduh. Pastikan pesan yang di-reply mengandung media."
            ).subscribe();
            return;
        }

        EmailSession session = new EmailSession(chatId, phone, fromName != null ? fromName : phone, recipient);
        session.addMedia(media);

        log.info("Reply-forward: sending 1 media directly to {} for {}", recipient, phone);
        emailForwardService.send(session);
    }

    private String resolveRecipient(String body) {
        String[] parts = body.split("\\s+", 2);
        if (parts.length == 1) {
            // ".email" saja → pakai default
            return defaultRecipient;
        }
        String customEmail = parts[1].trim();
        if (customEmail.matches(EMAIL_REGEX)) {
            return customEmail;
        }
        return null;
    }

    /**
     * Menutup sesi dan langsung mengirim email tanpa menunggu TTL.
     */
    public void handleDone(WhatsAppWebhookDTO webhook) {
        String phone = webhook.getCleanSenderId();
        EmailSession session = sessionCache.remove(phone);

        if (session == null) {
            whatsAppSenderService.sendWhatsAppText(webhook.buildChatId(),
                "⚠️ Tidak ada sesi email aktif. Ketik *.email* untuk mulai."
            ).subscribe();
            return;
        }

        log.info("Email session closed by user {} — {} media collected",
            phone, session.getMediaList().size());
        emailForwardService.send(session);
    }

    /**
     * Menambahkan media dari pesan yang masuk ke sesi aktif user.
     * Dipanggil oleh {@link org.cekpelunasan.platform.whatsapp.service.Routers}
     * ketika user memiliki sesi aktif dan pesan mengandung media.
     */
    public void collectMedia(WhatsAppWebhookDTO webhook) {
        String phone = webhook.getCleanSenderId();
        EmailSession session = sessionCache.get(phone);
        if (session == null) return;

        String mediaType = webhook.getMediaType();
        MediaPayloadDTO media = webhook.getMedia();
        if (media == null || mediaType == null) return;

        String downloadUrl = resolveDownloadUrl(media);
        if (downloadUrl == null) {
            log.warn("Media from {} has no path or url, skipping", phone);
            return;
        }

        String filename = resolveFilename(media, mediaType, session.getMediaList().size() + 1);
        session.addMedia(new EmailSession.CollectedMedia(downloadUrl, filename, mediaType, media.getCaption()));

        log.info("Collected {} media for session {}: {}", mediaType, phone, filename);
    }

    private String resolveDownloadUrl(MediaPayloadDTO media) {
        if (media.getPath() != null && !media.getPath().isBlank()) return media.getPath();
        if (media.getUrl() != null && !media.getUrl().isBlank()) return media.getUrl();
        return null;
    }

    private String resolveFilename(MediaPayloadDTO media, String mediaType, int index) {
        if (media.getFilename() != null && !media.getFilename().isBlank()) {
            return media.getFilename();
        }
        if (media.getPath() != null) {
            String path = media.getPath();
            String segment = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (!segment.isBlank()) return segment;
        }
        // Fallback: buat nama dari tipe dan urutan
        String ext = switch (mediaType) {
            case "image" -> ".jpg";
            case "video" -> ".mp4";
            case "audio" -> ".ogg";
            default -> ".bin";
        };
        return mediaType + "_" + index + ext;
    }
}
