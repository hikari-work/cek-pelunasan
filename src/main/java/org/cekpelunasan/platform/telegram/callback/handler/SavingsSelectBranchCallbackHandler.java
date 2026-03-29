package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsSelectBranchCallbackHandler extends AbstractCallbackHandler {

    private final SavingsService savingsService;
    private final PaginationSavingsButton paginationSavingsButton;
    private final SavingsUtils savingsUtils;

    @Override
    public String getCallBackData() {
        return "branchtab";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            log.info("Selecting Branch For Savings");
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] data = callbackData.split("_");
            String branchName = data[1];
            String query = data[2];
            long chatId = update.chatId;
            long messageId = update.messageId;
            Page<Savings> savings = savingsService.findByNameAndBranch(query, branchName, 0);
            if (savings.isEmpty()) {
                log.info("Branch Tab is Not Found...");
                sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                return;
            }
            log.info("Sending Savings Data");
            TdApi.ReplyMarkupInlineKeyboard markup = paginationSavingsButton.keyboardMarkup(savings, branchName, 0, query);
            editMessageWithMarkup(chatId, messageId, savingsUtils.buildMessage(savings, 0, System.currentTimeMillis()), client, markup);
        });
    }

    public String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }
}
