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

@Slf4j
@Component
public class DatabaseUpdateListener {

    private final UserService userService;
    private final TelegramMessageService telegramMessageService;
    private final TelegramBot telegramBot;

    public DatabaseUpdateListener(
            UserService userService,
            TelegramMessageService telegramMessageService,
            @Lazy TelegramBot telegramBot) {
        this.userService = userService;
        this.telegramMessageService = telegramMessageService;
        this.telegramBot = telegramBot;
    }

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
