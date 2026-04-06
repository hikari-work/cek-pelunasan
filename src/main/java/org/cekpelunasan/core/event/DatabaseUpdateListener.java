package org.cekpelunasan.core.event;

import it.tdlight.client.SimpleTelegramClient;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.bot.TelegramBot;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Pendengar event yang bertugas mengirim notifikasi Telegram ke semua pengguna
 * setiap kali ada pembaruan database.
 * <p>
 * Ketika ada proses import data selesai — baik berhasil maupun gagal — class ini
 * akan menerima {@link DatabaseUpdateEvent} dan langsung broadcast pesan ke
 * seluruh pengguna yang terdaftar di sistem. Pesan yang dikirim berisi emoji
 * status (centang hijau atau silang merah), nama database, dan waktu update.
 * </p>
 * <p>
 * Proses pengiriman notifikasi berjalan secara asinkron (non-blocking) agar
 * tidak menghambat proses import yang sedang berjalan.
 * </p>
 */
@Slf4j
@Component
public class DatabaseUpdateListener {

    private final UserService userService;
    private final TelegramMessageService telegramMessageService;

    /**
     * Client Telegram (TDLight) yang dipakai untuk mengirim pesan.
     * Diinject secara {@code @Lazy} untuk menghindari circular dependency
     * dengan {@link TelegramBot} yang juga butuh komponen lain saat startup.
     */
    private final TelegramBot telegramBot;

    /**
     * Membangun listener dengan semua dependency yang dibutuhkan untuk
     * mengirim pesan Telegram ke pengguna.
     *
     * @param userService            service untuk mengambil daftar semua pengguna terdaftar
     * @param telegramMessageService service untuk mengirim pesan teks ke chat Telegram
     * @param telegramBot            bot Telegram yang menyediakan TDLight client aktif
     */
    public DatabaseUpdateListener(
            UserService userService,
            TelegramMessageService telegramMessageService,
            @Lazy TelegramBot telegramBot) {
        this.userService = userService;
        this.telegramMessageService = telegramMessageService;
        this.telegramBot = telegramBot;
    }

    /**
     * Menangani event pembaruan database dan menyebarkan notifikasi ke semua pengguna.
     * <p>
     * Method ini dipanggil otomatis oleh Spring setiap kali ada {@link DatabaseUpdateEvent}
     * dipublikasikan. Jika client Telegram belum siap (misalnya bot belum login),
     * notifikasi akan dilewati dan dicatat di log.
     * </p>
     *
     * @param event event yang berisi informasi jenis database dan status keberhasilannya
     */
    @Async
    @EventListener(DatabaseUpdateEvent.class)
    public void onDatabaseUpdateEvent(DatabaseUpdateEvent event) {
        log.info("Processing database update event: {}", event.getEventType());
        try {
            SimpleTelegramClient client = telegramBot.getClient();
            if (client == null) {
                log.warn("TDLight client not ready, skipping notification");
                return;
            }
            String message = buildEventMessage(event);
            userService.findAllUsers()
                .doOnNext(user -> telegramMessageService.sendText(user.getChatId(), message, client))
                .doOnError(e -> log.error("Error sending notification to user", e))
                .doOnComplete(() -> log.info("Database update event processing completed"))
                .subscribe();
        } catch (Exception e) {
            log.error("Error processing database update event", e);
        }
    }

    /**
     * Menyusun teks pesan notifikasi yang akan dikirim ke pengguna.
     * <p>
     * Format pesan: {@code [emoji] Database [nama_db] [berhasil/gagal] di update pada [tanggal jam]:}
     * Tanggal ditampilkan dalam bahasa Indonesia dengan format "dd MMMM yyyy HH:mm:ss".
     * </p>
     *
     * @param event event yang berisi informasi untuk disusun menjadi pesan
     * @return teks pesan yang siap dikirim ke Telegram
     */
    private String buildEventMessage(DatabaseUpdateEvent event) {
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm:ss",
                Locale.forLanguageTag("id-ID")));
        String emoji = event.isSuccess() ? "✅" : "❌";
        String statusText = event.isSuccess() ? "berhasil" : "gagal";
        return String.format("%s Database %s %s di update pada %s:",
            emoji, event.getEventType().value, statusText, timestamp);
    }
}
