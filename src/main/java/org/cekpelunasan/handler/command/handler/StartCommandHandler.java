package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.AuthorizedChats;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class StartCommandHandler implements CommandProcessor {

    private final AuthorizedChats authService;
    private final MessageTemplate messageTemplateService;

    private static final String START_MESSAGE = """
            👋 *PONG!!!*
            """;

    public StartCommandHandler(AuthorizedChats authService, MessageTemplate messageTemplateService) {
        this.authService = authService;
        this.messageTemplateService = messageTemplateService;
    }

    @Override
    public String getCommand() {
        return "/start";
    }

    @Override
    public String getDescription() {
        return """
                Mengecek Bot Apakah Aktif
                """;
    }
    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            Long chatId = update.getMessage().getChatId();
            if (authService.isAuthorized(chatId)) {
                sendMessage(chatId, START_MESSAGE, telegramClient);
            } else {
                sendMessage(chatId, messageTemplateService.unathorizedMessage(), telegramClient);
            }
        });
    }
}
