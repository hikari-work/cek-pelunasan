package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.minbunga.MinBungaSessionService;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.utils.MinBungaCalendarBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinBungaBranchCallbackHandler extends AbstractCallbackHandler {

    private final MinBungaSessionService sessionService;
    private final MinBungaCalendarBuilder calendarBuilder;

    @Override
    public String getCallBackData() {
        return "minbungabranch";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 2);
        if (parts.length < 2) {
            return runBlocking(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String branch = parts[1];
        long chatId = update.chatId;
        long messageId = update.messageId;

        log.info("MinBunga branch selected: {} by chat {}", branch, chatId);

        return sessionService.getOrCreate(chatId, branch, "BRANCH")
            .flatMap(session -> runBlocking(() -> {
                TdApi.ReplyMarkupInlineKeyboard calendar =
                    calendarBuilder.buildCalendar(branch, new ArrayList<>(), false);
                editMessageWithMarkup(chatId, messageId,
                    "📅 *Pilih Tanggal Penagihan* — Cabang: " + branch + "\n\n" +
                    "_Pilih satu atau beberapa tanggal target penagihan._\n" +
                    "_Bot akan menampilkan nasabah yang DayLate-nya tidak melebihi 90 hari pada tanggal tersebut._",
                    client, calendar);
            }))
            .then();
    }
}
