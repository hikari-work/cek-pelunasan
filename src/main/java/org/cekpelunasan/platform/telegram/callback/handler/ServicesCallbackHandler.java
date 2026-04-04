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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] parts = callbackData.split("_", 3);
            if (parts.length < 3) {
                log.error("Callback data not valid: {}", callbackData);
                sendMessage(update.chatId, "❌ *Data callback tidak valid*", client);
                return;
            }

            String service = parts[1];
            String query = parts[2];
            long chatId = update.chatId;
            long messageId = update.messageId;

            log.info("Services callback: service={}, query={}", service, query);

            switch (service) {
                case "Pelunasan" -> handlePelunasan(chatId, messageId, query, client);
                case "Tabungan" -> handleTabungan(chatId, messageId, query, client);
                default -> {
                    log.warn("Unknown service: {}", service);
                    sendMessage(chatId, "❌ *Layanan tidak dikenali*", client);
                }
            }
        });
    }

    private void handlePelunasan(long chatId, long messageId, String query, SimpleTelegramClient client) {
        Set<String> branches = billService.lisAllBranch().block();
        if (branches.isEmpty()) {
            sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
            return;
        }
        editMessageWithMarkup(chatId, messageId,
            "🏦 *Pilih Cabang untuk Pelunasan*\n\nNasabah: *" + query + "*",
            client,
            buttonListForSelectBranch.dynamicSelectBranch(branches, query));
    }

    private void handleTabungan(long chatId, long messageId, String query, SimpleTelegramClient client) {
        Set<String> branches = savingsService.listAllBranch(query).block();
        if (branches.isEmpty()) {
            sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
            return;
        }
        editMessageWithMarkup(chatId, messageId,
            "💰 *Pilih Cabang untuk Tabungan*\n\nNasabah: *" + query + "*",
            client,
            selectSavingsBranch.dynamicSelectBranch(branches, query));
    }
}
