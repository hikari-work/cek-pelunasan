package org.cekpelunasan.core.service.slik;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.slik.dto.SlikJsonDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menyimpan sesi SLIK pengguna sementara di memori (in-memory cache).
 * Ketika pengguna menjalankan perintah {@code /slik}, hasil pencariannya
 * (yang bisa terdiri dari beberapa halaman) disimpan di sini sehingga
 * pengguna bisa berpindah halaman tanpa harus memuat ulang data dari S3.
 *
 * <p>Setiap sesi memiliki waktu kadaluarsa 30 menit. Sesi yang sudah
 * kadaluarsa dibersihkan otomatis setiap 5 menit oleh tugas terjadwal
 * {@link #cleanup()} agar memori tidak terus bertambah.</p>
 *
 * <p>Cache ini aman untuk diakses dari banyak thread sekaligus karena
 * menggunakan {@link ConcurrentHashMap} sebagai penyimpanan.</p>
 */
@Slf4j
@Component
public class SlikSessionCache {

    /** Waktu kadaluarsa sesi: 30 menit dalam milidetik. */
    private static final long TTL_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<Long, CachedSession> sessions = new ConcurrentHashMap<>();

    /**
     * Data satu halaman dalam sesi SLIK: nama file di S3, nomor KTP yang
     * diekstrak dari PDF, dan DTO JSON SLIK yang sudah diparsing (bisa null
     * jika file tidak memiliki data SLIK yang valid).
     */
    public record SlikPageData(String contentKey, String idNumber, SlikJsonDto dto) {}

    /**
     * Satu sesi SLIK lengkap milik seorang pengguna: berisi semua halaman
     * yang perlu ditampilkan, dan kata kunci pencarian yang digunakan.
     */
    public record SlikSession(List<SlikPageData> pages, String query) {}

    /** Wrapper internal yang menyimpan sesi beserta waktu kadaluarsanya. */
    private record CachedSession(SlikSession session, long expiresAt) {}

    /**
     * Menyimpan sesi SLIK untuk pengguna tertentu. Jika sudah ada sesi
     * sebelumnya untuk chat ID yang sama, sesi lama akan ditimpa.
     * Waktu kadaluarsa diset 30 menit dari sekarang.
     *
     * @param chatId  ID chat Telegram pengguna
     * @param session data sesi SLIK yang ingin disimpan
     */
    public void put(long chatId, SlikSession session) {
        sessions.put(chatId, new CachedSession(session, System.currentTimeMillis() + TTL_MS));
    }

    /**
     * Mengambil sesi SLIK milik pengguna tertentu. Jika sesi sudah kadaluarsa,
     * sesi dihapus dari cache dan null dikembalikan agar pengguna diminta
     * menjalankan perintah {@code /slik} ulang.
     *
     * @param chatId ID chat Telegram pengguna
     * @return objek {@link SlikSession} jika masih valid, atau {@code null} jika tidak ada/kadaluarsa
     */
    public SlikSession get(long chatId) {
        CachedSession cached = sessions.get(chatId);
        if (cached == null) return null;
        if (System.currentTimeMillis() > cached.expiresAt()) {
            sessions.remove(chatId);
            return null;
        }
        return cached.session();
    }

    /**
     * Membersihkan sesi-sesi yang sudah kadaluarsa dari cache. Tugas ini
     * berjalan otomatis setiap 5 menit. Hasilnya dicatat ke log hanya jika
     * ada sesi yang benar-benar dihapus.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("SlikSessionCache cleanup: removed {} expired sessions, remaining {}", removed, sessions.size());
        }
    }
}
