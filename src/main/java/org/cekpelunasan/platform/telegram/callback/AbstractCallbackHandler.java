package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public abstract class AbstractCallbackHandler implements CallbackProcessor {

    @Autowired
    protected TelegramMessageService telegramMessageService;

    protected void sendMessage(long chatId, String text, SimpleTelegramClient client) {
        telegramMessageService.sendText(chatId, text, client);
    }

    protected void editMessageWithMarkup(long chatId, long messageId, String text, SimpleTelegramClient client, TdApi.ReplyMarkupInlineKeyboard markup) {
        telegramMessageService.editMessageWithMarkup(chatId, messageId, text, markup, client);
    }
}
