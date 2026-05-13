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

@Slf4j
@Component
@RequiredArgsConstructor
public class MinBungaCalendarCallbackHandler extends AbstractCallbackHandler {

    private final MinBungaSessionService sessionService;
    private final MinBungaCalendarBuilder calendarBuilder;

    @Override
    public String getCallBackData() {
        return "minbungacal";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        // Format: minbungacal_<identifier>_<YYYY-MM-DD>
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 3);
        if (parts.length < 3) {
            return runBlocking(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String identifier = parts[1];
        String date = parts[2];
        long chatId = update.chatId;
        long messageId = update.messageId;

        log.info("MinBunga calendar toggle: {} date {} by chat {}", identifier, date, chatId);

        return sessionService.toggleDate(chatId, date)
            .switchIfEmpty(runBlocking(() ->
                sendMessage(chatId, "⚠️ *Sesi habis, jalankan ulang* `/minbunga`", client)))
            .flatMap(session -> runBlocking(() -> {
                boolean hasSelection = !session.getSelectedDates().isEmpty();
                TdApi.ReplyMarkupInlineKeyboard calendar =
                    calendarBuilder.buildCalendar(identifier, session.getSelectedDates(), hasSelection);

                String role = session.getRole() != null ? session.getRole() : "BRANCH";
                String caption = "AO".equals(role)
                    ? "📅 *Pilih Tanggal Penagihan*"
                    : "📅 *Pilih Tanggal Penagihan* — Cabang: " + identifier;

                caption += "\n\n_Pilih satu atau beberapa tanggal target penagihan._\n" +
                    "_Bot akan menampilkan nasabah yang DayLate-nya tidak melebihi 90 hari pada tanggal tersebut._";

                if (hasSelection) {
                    caption += "\n\n✅ *Tanggal terpilih: " + session.getSelectedDates().size() + "*";
                }

                editMessageWithMarkup(chatId, messageId, caption, client, calendar);
            }))
            .then();
    }
}
