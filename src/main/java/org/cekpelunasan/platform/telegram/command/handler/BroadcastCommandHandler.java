package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastCommandHandler extends AbstractCommandHandler {

    private static final long DELAY_BETWEEN_USERS_MS = 500;

    private final UserService userService;

    @Override
    public String getCommand() {
        return "/broadcast";
    }

    @Override
    public String getDescription() {
        return """
            Kirim pesan ke semua user terdaftar.
            Format: /broadcast <pesan>
            """;
    }

    @Override
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        long chatId = update.message.chatId;

        if (update.message.replyTo == null) {
            return Mono.fromRunnable(() ->
                sendMessage(chatId, "❗ *Format salah.*\nBalas pesan yang mau di-broadcast, lalu ketik `/broadcast`", client));
        }

        long replyMessageId = ((TdApi.MessageReplyToMessage) update.message.replyTo).messageId;

        return userService.findAllUsers()
            .collectList()
            .flatMap(allUsers -> Mono.fromRunnable(() -> {
                for (var user : allUsers) {
                    log.info("Copying To {}", user.getChatId());
                    copyMessage(chatId, replyMessageId, user.getChatId(), client);
                    try {
                        Thread.sleep(DELAY_BETWEEN_USERS_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted saat delay antar user", e);
                    }
                }
                sendMessage(chatId, "✅ Broadcast copyMessage selesai ke " + allUsers.size() + " pengguna.", client);
            }))
            .onErrorResume(e -> {
                log.error("Gagal broadcast copyMessage", e);
                return Mono.fromRunnable(() -> sendMessage(chatId, "❗ Gagal melakukan broadcast salinan pesan.", client));
            })
            .then();
    }
}
