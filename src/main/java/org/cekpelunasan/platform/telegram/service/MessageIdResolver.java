package org.cekpelunasan.platform.telegram.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menjembatani antara local message ID sementara yang dikembalikan TDLib
 * saat pengiriman pesan, dengan server message ID final yang dikirimkan
 * melalui event {@code UpdateMessageSendSucceeded}.
 *
 * <p>TDLib bekerja dua tahap saat mengirim pesan:
 * <ol>
 *   <li>Callback {@code SendMessage} → mengembalikan objek {@code Message} dengan
 *       <em>local temporary ID</em> (biasanya sangat besar, contoh: 1048577).</li>
 *   <li>Event {@code UpdateMessageSendSucceeded} → diterima setelah server Telegram
 *       mengonfirmasi pengiriman, berisi {@code oldMessageId} (local) dan
 *       {@code message.id} (ID final di server).</li>
 * </ol>
 * </p>
 *
 * <p>Class ini menyimpan map {@code localId → CompletableFuture<serverId>}
 * sehingga {@link TelegramMessageService#sendTextVerified} bisa menunggu
 * ID server yang valid sebelum meneruskan ke pemanggil.</p>
 */
@Slf4j
@Service
public class MessageIdResolver {

    private final ConcurrentHashMap<Long, CompletableFuture<Long>> pending = new ConcurrentHashMap<>();

    /**
     * Mendaftarkan local ID ke dalam registry. Harus dipanggil <em>sebelum</em>
     * {@link #resolve} agar tidak ada race condition saat event cepat datang.
     *
     * @param localId local temporary message ID dari callback {@code SendMessage}
     */
    public void register(long localId) {
        pending.put(localId, new CompletableFuture<>());
        log.debug("Registered local message ID {} — menunggu server ID", localId);
    }

    /**
     * Mengambil future yang sudah didaftarkan untuk {@code localId}.
     *
     * @param localId local temporary message ID yang sudah didaftarkan via {@link #register}
     * @return future yang akan berisi server ID, atau future yang langsung selesai dengan 0
     *         jika ID belum terdaftar
     */
    public CompletableFuture<Long> getFuture(long localId) {
        CompletableFuture<Long> future = pending.get(localId);
        if (future == null) {
            log.warn("Tidak ada future terdaftar untuk local ID {}", localId);
            return CompletableFuture.completedFuture(0L);
        }
        return future;
    }

    /**
     * Menyelesaikan future yang terdaftar untuk {@code localId} dengan server ID-nya.
     * Dipanggil oleh {@link org.cekpelunasan.platform.telegram.bot.TelegramBot}
     * saat menerima {@code UpdateMessageSendSucceeded}.
     *
     * @param localId  local temporary ID yang sebelumnya didaftarkan
     * @param serverId ID final yang diberikan oleh server Telegram
     */
    public void resolve(long localId, long serverId) {
        CompletableFuture<Long> future = pending.remove(localId);
        if (future != null) {
            log.debug("Resolved local ID {} → server ID {}", localId, serverId);
            future.complete(serverId);
        }
    }

    /**
     * Membatalkan dan menghapus future terdaftar untuk {@code localId}.
     * Dipanggil saat timeout agar tidak ada memory leak.
     *
     * @param localId local ID yang ingin dibatalkan
     */
    public void cancel(long localId) {
        CompletableFuture<Long> future = pending.remove(localId);
        if (future != null) {
            future.cancel(false);
            log.debug("Cancelled pending future untuk local ID {}", localId);
        }
    }
}
