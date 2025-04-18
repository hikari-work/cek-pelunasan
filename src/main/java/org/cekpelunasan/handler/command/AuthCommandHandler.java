package org.cekpelunasan.handler.command;
import org.cekpelunasan.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class AuthCommandHandler implements CommandProcessor {

    private final AuthorizedChats authorizedChats1;
    @Value("${telegram.bot.owner}")
    private Long ownerId;

    private final UserService userService;
    private final MessageTemplate messageTemplateService;

    public AuthCommandHandler(UserService userService, MessageTemplate messageTemplateService, AuthorizedChats authorizedChats1) {
        this.userService = userService;
        this.messageTemplateService = messageTemplateService;
        this.authorizedChats1 = authorizedChats1;
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
            sendMessage(senderId, messageTemplateService.notAdminUsers(), telegramClient);
            return;
        }

        if (parts.length < 2) {
            sendMessage(senderId, messageTemplateService.notValidDeauthFormat(), telegramClient);
            return;
        }

        try {
            long chatIdTarget = Long.parseLong(parts[1]);
            userService.insertNewUsers(chatIdTarget);
            sendMessage(chatIdTarget, messageTemplateService.authorizedMessage(), telegramClient);
            authorizedChats1.addAuthorizedChat(chatIdTarget);
            sendMessage(ownerId, "Sukses", telegramClient);
        } catch (NumberFormatException e) {
            sendMessage(senderId, messageTemplateService.notValidNumber(), telegramClient);
        }
    }
}
