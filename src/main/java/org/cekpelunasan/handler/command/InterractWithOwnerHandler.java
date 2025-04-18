package org.cekpelunasan.handler.command;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class InterractWithOwnerHandler implements CommandProcessor{
    @Override
    public String getCommand() {
        return "/id";
    }

    @Override
    public void process(Update update, TelegramClient telegramClient) {

    }
}
