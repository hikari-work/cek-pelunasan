package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.MinBungaSession;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.minbunga.BillsForDate;
import org.cekpelunasan.core.service.minbunga.MinBungaBillCalculatorService;
import org.cekpelunasan.core.service.minbunga.MinBungaSessionService;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.utils.MinBungaMessageFormatter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinBungaConfirmCallbackHandler extends AbstractCallbackHandler {

    private final MinBungaSessionService sessionService;
    private final BillService billService;
    private final MinBungaMessageFormatter formatter;
    private final MinBungaBillCalculatorService calculator;

    @Override
    public String getCallBackData() {
        return "minbungaconfirm";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        long chatId = update.chatId;
        long messageId = update.messageId;

        log.info("MinBunga confirm from chat {}", chatId);

        return sessionService.getSession(chatId)
            .switchIfEmpty(runBlocking(() -> sendMessage(chatId, "❌ *Sesi tidak ditemukan. Mulai ulang dengan /minbunga*", client)))
            .flatMap(session -> {
                if (session.getSelectedDates() == null || session.getSelectedDates().isEmpty()) {
                    return runBlocking(() ->
                        sendMessage(chatId, "❌ *Belum ada tanggal yang dipilih.*", client)
                    );
                }

                boolean isAo = "AO".equals(session.getRole());
                Mono<List<org.cekpelunasan.core.entity.Bills>> billsMono = isAo
                    ? billService.findMinimalBungaByAccountOfficer(session.getIdentifier())
                    : billService.findMinimalBungaByBranch(session.getIdentifier());

                return billsMono.flatMap(allBills -> runBlocking(() -> {
                    log.info("MinBunga: {} bills fetched for {}", allBills.size(), session.getIdentifier());

                    // Edit pesan kalender menjadi pesan status proses
                    telegramMessageService.editText(chatId, messageId,
                        "⏳ *Sedang memproses data...*\n_Mohon tunggu sebentar._", client);

                    List<LocalDate> targetDates = session.getSelectedDates().stream()
                        .map(LocalDate::parse).toList();
                    List<BillsForDate> grouped = calculator.calculate(allBills, targetDates);
                    List<String> messages = formatter.format(grouped, session.getIdentifier());

                    for (String msg : messages) {
                        telegramMessageService.sendText(chatId, msg, client);
                    }

                    log.info("MinBunga: {} messages sent to chat {}", messages.size(), chatId);
                })).then(sessionService.deleteSession(chatId));
            })
            .then();
    }
}
