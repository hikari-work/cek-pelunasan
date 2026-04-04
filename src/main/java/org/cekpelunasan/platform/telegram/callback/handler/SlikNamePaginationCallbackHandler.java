package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.slik.SlikNameFormatter;
import org.cekpelunasan.core.service.slik.SlikSessionCache;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.utils.button.SlikNamePaginationButton;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlikNamePaginationCallbackHandler extends AbstractCallbackHandler {

    private final SlikSessionCache sessionCache;
    private final SlikNameFormatter formatter;
    private final SlikNamePaginationButton paginationButton;

    @Override
    public String getCallBackData() {
        return "slikn";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] parts = callbackData.split("_");

            int page;
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("Invalid page index in callback: {}", callbackData);
                return;
            }

            long chatId = update.chatId;
            SlikSessionCache.SlikSession session = sessionCache.get(chatId);

            if (session == null || session.pages().isEmpty()) {
                telegramMessageService.sendText(chatId, "⚠️ Sesi pencarian sudah habis, ulangi `/slik <nama>`", client);
                return;
            }

            List<SlikSessionCache.SlikPageData> pages = session.pages();
            if (page < 0 || page >= pages.size()) {
                log.warn("Page {} out of range ({}) for chat {}", page, pages.size(), chatId);
                return;
            }

            TdApi.FormattedText message = formatter.format(pages.get(page), page, pages.size());
            TdApi.ReplyMarkupInlineKeyboard keyboard = paginationButton.build(page, pages.size());
            telegramMessageService.editMessageWithFormattedMarkup(chatId, update.messageId, message, keyboard, client);

            log.info("SLIK name pagination — chat: {}, page: {}/{}", chatId, page + 1, pages.size());
        });
    }
}
