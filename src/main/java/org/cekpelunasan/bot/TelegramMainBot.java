package org.cekpelunasan.bot;

import org.cekpelunasan.handler.callback.CallbackHandler;
import org.cekpelunasan.handler.command.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramMainBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramMainBot.class);

    private final CommandHandler commandHandler;
    private final CallbackHandler callbackHandler;
    private final TelegramClient telegramClient;

    private final String botToken;

    public TelegramMainBot(
            CommandHandler commandHandler,
            CallbackHandler callbackHandler,
            @Value("${telegram.bot.token}") String botToken
    ) {
        this.commandHandler = commandHandler;
        this.callbackHandler = callbackHandler;
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        handleAsync(update);
    }
    @Async
    public void handleAsync(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                commandHandler.handle(update, telegramClient);
            } else if (update.hasCallbackQuery()) {
                callbackHandler.handle(update, telegramClient);
            } else {
                log.warn("Received an update that is neither a message nor a callback query: {}", update);
            }
        } catch (Exception e) {
            log.error("Error Handling Update", e);
        }
    }
}
