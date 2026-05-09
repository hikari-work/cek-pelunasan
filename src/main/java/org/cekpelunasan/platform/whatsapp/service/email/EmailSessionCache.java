package org.cekpelunasan.platform.whatsapp.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * Menyimpan sesi email aktif per pengirim WhatsApp dalam memori.
 *
 * <p>Setiap sesi punya TTL 60 detik. Jika dalam 60 detik user tidak mengetik .done,
 * callback auto-send akan dijalankan secara otomatis oleh TaskScheduler,
 * lalu sesi dihapus. Jika user mengetik .done lebih awal, sesi di-cancel dan dihapus.</p>
 */
@Slf4j
@Component
public class EmailSessionCache {

    private static final long TTL_MS = 60_000L;

    private final TaskScheduler taskScheduler;
    private final ConcurrentHashMap<String, EmailSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public EmailSessionCache(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * Membuat sesi baru untuk pengirim tertentu.
     * Jika sudah ada sesi sebelumnya, sesi lama dibatalkan terlebih dahulu.
     *
     * @param session      data sesi yang akan disimpan
     * @param onAutoSend   callback yang dipanggil saat TTL habis (auto-send)
     */
    public void put(EmailSession session, Consumer<EmailSession> onAutoSend) {
        String phone = session.getSenderPhone();

        // Batalkan sesi lama jika ada
        cancelScheduled(phone);

        sessions.put(phone, session);

        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> {
                EmailSession expired = sessions.remove(phone);
                scheduledTasks.remove(phone);
                if (expired != null) {
                    log.info("EmailSession TTL expired for {}, auto-sending", phone);
                    onAutoSend.accept(expired);
                }
            },
            Instant.now().plusMillis(TTL_MS)
        );

        scheduledTasks.put(phone, future);
        log.info("EmailSession created for {}, auto-send in {}s", phone, TTL_MS / 1000);
    }

    /**
     * Mengambil sesi aktif milik pengirim. Tidak menghapus sesi — TTL tetap berjalan.
     *
     * @param phone nomor HP bersih pengirim
     * @return sesi aktif, atau {@code null} jika tidak ada
     */
    public EmailSession get(String phone) {
        return sessions.get(phone);
    }

    /**
     * Menghapus sesi dan membatalkan auto-send yang sudah dijadwalkan.
     * Dipanggil saat user mengetik .done.
     *
     * @param phone nomor HP bersih pengirim
     * @return sesi yang dihapus, atau {@code null} jika tidak ada
     */
    public EmailSession remove(String phone) {
        cancelScheduled(phone);
        return sessions.remove(phone);
    }

    private void cancelScheduled(String phone) {
        ScheduledFuture<?> existing = scheduledTasks.remove(phone);
        if (existing != null) {
            existing.cancel(false);
        }
    }
}
