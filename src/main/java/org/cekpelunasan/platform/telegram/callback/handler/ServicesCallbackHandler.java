package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServicesCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;
    private final SavingsService savingsService;
    private final ButtonListForSelectBranch buttonListForSelectBranch;
    private final SelectSavingsBranch selectSavingsBranch;

    @Override
    public String getCallBackData() {
        return "services";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 3);
        if (parts.length < 3) {
            log.error("Callback data not valid: {}", callbackData);
            return Mono.fromRunnable(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String service = parts[1];
        String query = parts[2];
        long chatId = update.chatId;
        long messageId = update.messageId;

        log.info("Services callback: service={}, query={}", service, query);

        return switch (service) {
            case "Pelunasan" -> handlePelunasan(chatId, messageId, query, client);
            case "Tabungan" -> handleTabungan(chatId, messageId, query, client);
            default -> {
                log.warn("Unknown service: {}", service);
                yield Mono.fromRunnable(() -> sendMessage(chatId, "❌ *Layanan tidak dikenali*", client));
            }
        };
    }

    private Mono<Void> handlePelunasan(long chatId, long messageId, String query, SimpleTelegramClient client) {
        return billService.lisAllBranch()
            .flatMap(branches -> Mono.fromRunnable(() -> {
                if (branches.isEmpty()) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                editMessageWithMarkup(chatId, messageId,
                    "🏦 *Pilih Cabang untuk Pelunasan*\n\nNasabah: *" + query + "*",
                    client,
                    buttonListForSelectBranch.dynamicSelectBranch(branches, query));
            }))
            .then();
    }

    private Mono<Void> handleTabungan(long chatId, long messageId, String query, SimpleTelegramClient client) {
        return savingsService.listAllBranch(query)
            .flatMap(branches -> Mono.fromRunnable(() -> {
                if (branches.isEmpty()) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                editMessageWithMarkup(chatId, messageId,
                    "💰 *Pilih Cabang untuk Tabungan*\n\nNasabah: *" + query + "*",
                    client,
                    selectSavingsBranch.dynamicSelectBranch(branches, query));
            }))
            .then();
    }
}
