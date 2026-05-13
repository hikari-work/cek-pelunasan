package org.cekpelunasan.core.service.minbunga;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.MinBungaSession;
import org.cekpelunasan.core.repository.MinBungaSessionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class MinBungaSessionService {

    private final MinBungaSessionRepository repository;

    public Mono<MinBungaSession> getOrCreate(long chatId, String identifier, String role) {
        String key = String.valueOf(chatId);
        return repository.findById(key)
            .flatMap(existing -> {
                existing.setIdentifier(identifier);
                existing.setRole(role);
                existing.setSelectedDates(new ArrayList<>());
                existing.setMessageId(null);
                existing.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Jakarta")));
                return repository.save(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                MinBungaSession session = MinBungaSession.builder()
                    .chatId(key)
                    .identifier(identifier)
                    .role(role)
                    .createdAt(LocalDateTime.now(ZoneId.of("Asia/Jakarta")))
                    .build();
                return repository.save(session);
            }));
    }

    public Mono<MinBungaSession> setMessageId(long chatId, long messageId) {
        String key = String.valueOf(chatId);
        return repository.findById(key)
            .flatMap(session -> {
                session.setMessageId(messageId);
                return repository.save(session);
            });
    }

    public Mono<MinBungaSession> toggleDate(long chatId, String date) {
        String key = String.valueOf(chatId);
        return repository.findById(key)
            .flatMap(session -> {
                if (session.getSelectedDates().contains(date)) {
                    session.getSelectedDates().remove(date);
                } else {
                    session.getSelectedDates().add(date);
                }
                return repository.save(session);
            });
    }

    public Mono<MinBungaSession> clearDates(long chatId) {
        String key = String.valueOf(chatId);
        return repository.findById(key)
            .flatMap(session -> {
                session.setSelectedDates(new ArrayList<>());
                return repository.save(session);
            });
    }

    public Mono<MinBungaSession> getSession(long chatId) {
        return repository.findById(String.valueOf(chatId));
    }

    public Mono<Void> deleteSession(long chatId) {
        return repository.deleteById(String.valueOf(chatId));
    }
}
