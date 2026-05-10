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
public class MinBungaClearCallbackHandler extends AbstractCallbackHandler {

    private final MinBungaSessionService sessionService;
    private final MinBungaCalendarBuilder calendarBuilder;

    @Override
    public String getCallBackData() {
        return "minbungaclear";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        // Format: minbungaclear_<identifier>
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 2);
        if (parts.length < 2) {
            return runBlocking(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String identifier = parts[1];
        long chatId = update.chatId;
        long messageId = update.messageId;

        log.info("MinBunga clear dates for chat {}", chatId);

        return sessionService.clearDates(chatId)
            .flatMap(session -> runBlocking(() -> {
                TdApi.ReplyMarkupInlineKeyboard calendar =
                    calendarBuilder.buildCalendar(identifier, new ArrayList<>(), false);

                String role = session.getRole() != null ? session.getRole() : "BRANCH";
                String caption = "AO".equals(role)
                    ? "📅 *Pilih Tanggal Penagihan*"
                    : "📅 *Pilih Tanggal Penagihan* — Cabang: " + identifier;
                caption += "\n\n_Pilih satu atau beberapa tanggal target penagihan._\n" +
                    "_Bot akan menampilkan nasabah yang DayLate-nya tidak melebihi 90 hari pada tanggal tersebut._";

                editMessageWithMarkup(chatId, messageId, caption, client, calendar);
            }))
            .then();
    }
}
