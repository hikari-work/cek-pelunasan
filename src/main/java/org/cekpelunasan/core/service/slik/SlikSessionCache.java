package org.cekpelunasan.core.service.slik;

import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.slik.dto.SlikJsonDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SlikSessionCache {

    private static final long TTL_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<Long, CachedSession> sessions = new ConcurrentHashMap<>();

    public record SlikPageData(String contentKey, String idNumber, SlikJsonDto dto) {}

    public record SlikSession(List<SlikPageData> pages, String query) {}

    private record CachedSession(SlikSession session, long expiresAt) {}

    public void put(long chatId, SlikSession session) {
        sessions.put(chatId, new CachedSession(session, System.currentTimeMillis() + TTL_MS));
    }

    public SlikSession get(long chatId) {
        CachedSession cached = sessions.get(chatId);
        if (cached == null) return null;
        if (System.currentTimeMillis() > cached.expiresAt()) {
            sessions.remove(chatId);
            return null;
        }
        return cached.session();
    }

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
