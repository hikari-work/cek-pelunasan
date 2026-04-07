package org.cekpelunasan.miniapp.auth;

import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Penyimpan sesi aktif Mini App di memori. Setiap sesi punya token unik (UUID)
 * dan kedaluwarsa setelah TTL yang dikonfigurasi. Sesi yang kedaluwarsa dibersihkan
 * otomatis setiap 5 menit oleh scheduled task.
 */
@Component
public class MiniAppSessionStore {

    private static final Logger log = LoggerFactory.getLogger(MiniAppSessionStore.class);

    @Value("${miniapp.session.ttl-minutes:60}")
    private int ttlMinutes;

    private final ConcurrentHashMap<String, MiniAppSession> sessions = new ConcurrentHashMap<>();

    /**
     * Membuat sesi baru untuk pengguna yang sudah terverifikasi.
     */
    public MiniAppSession create(Long chatId, AccountOfficerRoles roles) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds((long) ttlMinutes * 60);
        MiniAppSession session = new MiniAppSession(token, chatId, roles, expiresAt);
        sessions.put(token, session);
        return session;
    }

    /**
     * Mengambil sesi berdasarkan token. Mengembalikan empty jika token tidak ada
     * atau sudah kedaluwarsa, dan langsung menghapus sesi yang kedaluwarsa.
     */
    public Optional<MiniAppSession> get(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        MiniAppSession session = sessions.get(token);
        if (session == null) return Optional.empty();
        if (Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Menghapus sesi secara eksplisit (logout).
     */
    public void invalidate(String token) {
        sessions.remove(token);
    }

    /**
     * Membersihkan sesi yang sudah kedaluwarsa setiap 5 menit.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.debug("Mini App session cleanup: {} sesi kedaluwarsa dihapus", removed);
        }
    }
}
