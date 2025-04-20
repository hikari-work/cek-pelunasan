package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.GenerateHelpMessage;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class HelpCommandHandler implements CommandProcessor {
    private final MessageTemplate messageTemplate;
    private final GenerateHelpMessage generateHelpMessage;

    public HelpCommandHandler(MessageTemplate messageTemplate, @Lazy GenerateHelpMessage generateHelpMessage) {
        this.messageTemplate = messageTemplate;
        this.generateHelpMessage = generateHelpMessage;
    }

    @Override
    public String getCommand() {
        return "/help";
    }

    @Override
    public String getDescription() {
        return """
                Menampilkan pesan Ini
                """;
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            if (isHelpCommand(update)) {
                sendHelpMessage(update.getMessage().getChatId(), telegramClient);
            }
        });
    }

    private boolean isHelpCommand(Update update) {
        String messageText = update.getMessage().getText();
        return messageText != null && messageText.trim().startsWith(getCommand());
    }

    private void sendHelpMessage(Long chatId, TelegramClient telegramClient) {
        String helpMessage = generateHelpMessage.generateHelpText();
        sendMessage(chatId, helpMessage, telegramClient);
    }
}
