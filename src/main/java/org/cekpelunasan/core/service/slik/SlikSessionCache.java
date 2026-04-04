package org.cekpelunasan.core.service.slik;

import org.cekpelunasan.core.service.slik.dto.SlikJsonDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlikSessionCache {

    private final ConcurrentHashMap<Long, SlikSession> sessions = new ConcurrentHashMap<>();

    public record SlikPageData(String contentKey, String idNumber, SlikJsonDto dto) {}

    public record SlikSession(List<SlikPageData> pages, String query) {}

    public void put(long chatId, SlikSession session) {
        sessions.put(chatId, session);
    }

    public SlikSession get(long chatId) {
        return sessions.get(chatId);
    }
}
