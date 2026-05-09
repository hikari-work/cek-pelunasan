package org.cekpelunasan.platform.whatsapp.service.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.whatsapp.service.sender.WhatsAppSenderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.activation.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Mendownload media dari WhatsApp gateway lalu mengirimkannya sebagai attachment email.
 *
 * <p>Alur kerja: ambil setiap media dari session → download bytes via gateway WebClient →
 * attach ke MimeMessage → kirim via JavaMailSender → kirim konfirmasi WhatsApp ke user.</p>
 */
@Slf4j
@Service
public class EmailForwardService {

    private static final DateTimeFormatter SUBJECT_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final WebClient whatsappWebClient;
    private final WhatsAppSenderService whatsAppSenderService;

    @Value("${email.forward.from}")
    private String from;

    @Value("${whatsapp.gateway.url}")
    private String gatewayUrl;

    public EmailForwardService(JavaMailSender mailSender,
                               WebClient whatsappWebClient,
                               WhatsAppSenderService whatsAppSenderService) {
        this.mailSender = mailSender;
        this.whatsappWebClient = whatsappWebClient;
        this.whatsAppSenderService = whatsAppSenderService;
    }

    /**
     * Mengirim email berisi semua media yang terkumpul dalam sesi.
     * Dipanggil saat user ketik .done atau saat TTL habis.
     *
     * @param session sesi email yang siap dikirim
     */
    @Async
    public void send(EmailSession session) {
        if (session.getMediaList().isEmpty()) {
            notifyUser(session, "❌ Tidak ada media yang terkumpul. Email tidak dikirim.");
            return;
        }

        List<AttachmentData> attachments = downloadAttachments(session);

        if (attachments.isEmpty()) {
            notifyUser(session, "❌ Semua media gagal didownload. Email tidak dikirim.");
            return;
        }

        try {
            sendEmail(session, attachments);
            String info = String.format(
                "✅ Email berhasil dikirim ke %s\n📎 %d dari %d file terkirim.",
                session.getRecipient(), attachments.size(), session.getMediaList().size()
            );
            notifyUser(session, info);
            log.info("Email forwarded for {} — {}/{} attachments",
                session.getSenderPhone(), attachments.size(), session.getMediaList().size());
        } catch (Exception e) {
            log.error("Failed to send email for {}: {}", session.getSenderPhone(), e.getMessage(), e);
            notifyUser(session, "❌ Gagal mengirim email. Silakan coba lagi.");
        }
    }

    private List<AttachmentData> downloadAttachments(EmailSession session) {
        List<AttachmentData> result = new ArrayList<>();
        for (EmailSession.CollectedMedia media : session.getMediaList()) {
            try {
                byte[] bytes = downloadMedia(media.downloadUrl());
                result.add(new AttachmentData(media.filename(), bytes, resolveMimeType(media.mediaType())));
                log.info("Downloaded {} ({} bytes)", media.filename(), bytes.length);
            } catch (Exception e) {
                log.warn("Failed to download {}: {}", media.downloadUrl(), e.getMessage());
            }
        }
        return result;
    }

    private byte[] downloadMedia(String url) {
        // Jika URL absolut (http/https), gunakan WebClient tanpa base URL.
        // Jika path relatif (statics/media/...), append ke gateway URL.
        String fullUrl = url.startsWith("http") ? url : gatewayUrl + "/" + url;
        byte[] bytes = WebClient.create(fullUrl)
            .get()
            .retrieve()
            .bodyToMono(byte[].class)
            .block();
        if (bytes == null) throw new IllegalStateException("Empty response for " + fullUrl);
        return bytes;
    }

    private void sendEmail(EmailSession session, List<AttachmentData> attachments) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(from);
        helper.setTo(session.getRecipient());
        helper.setSubject(buildSubject(session));
        helper.setText(buildBody(session), false);

        for (AttachmentData att : attachments) {
            helper.addAttachment(att.filename(), new ByteArrayDataSource(att.bytes(), att.mimeType()));
        }

        mailSender.send(message);
    }

    private String buildSubject(EmailSession session) {
        return String.format("WA Forward dari %s — %s",
            session.getFromName(), LocalDateTime.now().format(SUBJECT_FORMAT));
    }

    private String buildBody(EmailSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pesan diteruskan dari WhatsApp\n\n");
        sb.append("Pengirim : ").append(session.getFromName()).append("\n");
        sb.append("Nomor    : ").append(session.getSenderPhone()).append("\n\n");

        List<EmailSession.CollectedMedia> mediaList = session.getMediaList();
        if (!mediaList.isEmpty()) {
            sb.append("Media yang dilampirkan:\n");
            for (int i = 0; i < mediaList.size(); i++) {
                EmailSession.CollectedMedia m = mediaList.get(i);
                sb.append(i + 1).append(". ").append(m.filename());
                if (m.caption() != null && !m.caption().isBlank()) {
                    sb.append(" — ").append(m.caption());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String resolveMimeType(String mediaType) {
        return switch (mediaType) {
            case "image" -> "image/jpeg";
            case "video" -> "video/mp4";
            case "audio" -> "audio/ogg";
            case "document" -> "application/octet-stream";
            default -> "application/octet-stream";
        };
    }

    private void notifyUser(EmailSession session, String message) {
        whatsAppSenderService.sendWhatsAppText(session.getChatId(), message)
            .subscribe(
                ok -> {},
                e -> log.warn("Failed to notify user {}: {}", session.getSenderPhone(), e.getMessage())
            );
    }

    private record AttachmentData(String filename, byte[] bytes, String mimeType) {}

    /** DataSource sederhana dari byte array untuk attachment MimeMessage. */
    private static class ByteArrayDataSource implements DataSource {
        private final byte[] data;
        private final String contentType;

        ByteArrayDataSource(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        @Override public OutputStream getOutputStream() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(data);
            return out;
        }
        @Override public String getContentType() { return contentType; }
        @Override public String getName() { return ""; }
    }
}
