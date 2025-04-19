package org.cekpelunasan.handler.command;

import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class DeleteUserAccessCommand implements CommandProcessor{

    private final AuthorizedChats authorizedChats;

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    private final UserService userService;
    private final MessageTemplate messageTemplate;

    public DeleteUserAccessCommand(AuthorizedChats authorizedChats, UserService userService, MessageTemplate messageTemplate) {
        this.authorizedChats = authorizedChats;
        this.userService = userService;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String getCommand() {
        return "/deauth";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            Long senderId = update.getMessage().getChatId();
            String[] parts = update.getMessage().getText().split(" ");
            if (!senderId.equals(ownerId)) {
                sendMessage(senderId, messageTemplate.notAdminUsers(), telegramClient);
                return;
            }
            if (parts.length < 2) {
                sendMessage(senderId, messageTemplate.notValidDeauthFormat(), telegramClient);
                return;
            }
            try {
                long target = Long.parseLong(parts[1]);
                userService.deleteUser(target);
                authorizedChats.deleteUser(target);
                sendMessage(target, messageTemplate.unathorizedMessage(), telegramClient);
                sendMessage(ownerId, "Sukses", telegramClient);
            } catch (NumberFormatException e) {
                sendMessage(update.getMessage().getChatId(), messageTemplate.notValidNumber(), telegramClient);
            }
        });
    }
}
