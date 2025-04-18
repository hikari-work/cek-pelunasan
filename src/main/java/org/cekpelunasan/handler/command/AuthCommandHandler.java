package org.cekpelunasan.handler.command;

import org.cekpelunasan.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class AuthCommandHandler implements CommandProcessor {

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    private final UserService userService;
    private final CommandHandler commandHandler;

    public AuthCommandHandler(UserService userService, CommandHandler commandHandler) {
        this.userService = userService;
        this.commandHandler = commandHandler;
    }

    @Override
    public String getCommand() {
        return "/auth";
    }

    @Override
    public void process(Update update, TelegramClient telegramClient) {
        Long senderId = update.getMessage().getChatId();
        String[] parts = update.getMessage().getText().split(" ");

        if (!senderId.equals(ownerId)) {
            sendMessage(senderId, "❌ Kamu tidak punya izin untuk perintah ini.", telegramClient);
            return;
        }

        if (parts.length < 2) {
            sendMessage(senderId, "⚠️ Format salah. Contoh: /auth 123456789", telegramClient);
            return;
        }

        try {
            long chatIdTarget = Long.parseLong(parts[1]);
            userService.insertNewUser(chatIdTarget);
            sendMessage(chatIdTarget, commandHandler.sendWelcomeMessage(), telegramClient);
        } catch (NumberFormatException e) {
            sendMessage(senderId, "❌ ID harus berupa angka.", telegramClient);
        }
    }
}
