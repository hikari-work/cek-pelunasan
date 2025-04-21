package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.GenerateHelpMessage;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class HelpCommandHandler implements CommandProcessor {
    private final GenerateHelpMessage generateHelpMessage;

    public HelpCommandHandler(@Lazy GenerateHelpMessage generateHelpMessage) {
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
    public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            if (isHelpCommand(text)) {
                sendHelpMessage(chatId, telegramClient);
            }
        });
    }

    private boolean isHelpCommand(String text) {
        return text != null && text.trim().startsWith(getCommand());
    }

    private void sendHelpMessage(Long chatId, TelegramClient telegramClient) {
        String helpMessage = generateHelpMessage.generateHelpText();
        sendMessage(chatId, helpMessage, telegramClient);
    }
}
