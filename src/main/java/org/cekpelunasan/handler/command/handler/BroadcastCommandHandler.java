package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class BroadcastCommandHandler implements CommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(BroadcastCommandHandler.class);
    private static final long DELAY_BETWEEN_USERS_MS = 500;

    private final UserService userService;
    private final String botOwner;
    private final MessageTemplate messageTemplate;

    public BroadcastCommandHandler(UserService userService,
                                   @Value("${telegram.bot.owner}") String botOwner,
                                   MessageTemplate messageTemplate) {
        this.userService = userService;
        this.botOwner = botOwner;
        this.messageTemplate = messageTemplate;
    }

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
    public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            if (!botOwner.equalsIgnoreCase(String.valueOf(chatId))) {
                sendMessage(chatId, messageTemplate.notAdminUsers(), telegramClient);
                return;
            }

            try {
                String broadcastMessage = text.split(" ", 2)[1];
                List<User> allUsers = userService.findAllUsers();

                for (User user : allUsers) {
                    sendMessage(user.getChatId(), broadcastMessage, telegramClient);
                    try {
                        Thread.sleep(DELAY_BETWEEN_USERS_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted saat delay antar user", e);
                    }
                }

                sendMessage(chatId, "✅ Broadcast selesai ke " + allUsers.size() + " pengguna.", telegramClient);

            } catch (ArrayIndexOutOfBoundsException e) {
                sendMessage(chatId, "❗ *Format salah.*\nGunakan `/broadcast <pesan>`", telegramClient);
            }
        });
    }
}
