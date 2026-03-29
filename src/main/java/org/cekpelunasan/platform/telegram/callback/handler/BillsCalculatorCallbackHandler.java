package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.TagihanUtils;
import org.cekpelunasan.utils.button.BackKeyboardButtonForBillsUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Component
public class BillsCalculatorCallbackHandler extends AbstractCallbackHandler {
    private final BillService billService;
    private final TagihanUtils tagihanUtils;

    @Override
    public String getCallBackData() {
        return "tagihan";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        CompletableFuture.runAsync(() -> {
            log.info("Bills Update Received");
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] parts = callbackData.split("_", 5);
            Bills bills = billService.getBillById(parts[1]);
            log.info("Bill ID: {}", parts[1]);
            if (bills == null) {
                log.info("Bill ID Not Found");
                sendMessage(update.chatId, "❌ *Data tidak ditemukan*", client);
                return;
            }
            log.info("Sending Bills Message Message");
            editMessageWithMarkup(update.chatId, update.messageId, tagihanUtils.detailBills(bills), client, new BackKeyboardButtonForBillsUtils().backButton(callbackData));
        });
        return CompletableFuture.completedFuture(null);
    }
}
