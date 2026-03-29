package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    @Async
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.message.chatId;

            if (update.message.replyTo == null) {
                sendMessage(chatId, "❗ *Format salah.*\nBalas pesan yang mau di-broadcast, lalu ketik `/broadcast`", client);
                return;
            }

            long replyMessageId = ((TdApi.MessageReplyToMessage) update.message.replyTo).messageId;

            try {
                List<User> allUsers = userService.findAllUsers();

                for (User user : allUsers) {
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

            } catch (Exception e) {
                log.error("Gagal broadcast copyMessage", e);
                sendMessage(chatId, "❗ Gagal melakukan broadcast salinan pesan.", client);
            }
        });
    }
}
