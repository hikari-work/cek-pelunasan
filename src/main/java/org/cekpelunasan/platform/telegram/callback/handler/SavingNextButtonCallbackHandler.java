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
public class SavingNextButtonCallbackHandler extends AbstractCallbackHandler {

    private final SavingsService savingsService;
    private final PaginationSavingsButton paginationSavingsButton;
    private final SavingsUtils savingsUtils;

    @Override
    public String getCallBackData() {
        return "tab";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            log.info("Generating Saving Data....");
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] parts = callbackData.split("_");
            String query = parts[1];
            String branch = parts[2];
            int page = Integer.parseInt(parts[3]);
            long chatId = update.chatId;
            long messageId = update.messageId;
            Page<Savings> savings = savingsService.findByNameAndBranch(query, branch, page);
            if (savings.isEmpty()) {
                log.info("Saving data Updated...");
                sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                return;
            }
            String message = buildMessage(savings, page, System.currentTimeMillis());
            editMessageWithMarkup(chatId, messageId, message, client, paginationSavingsButton.keyboardMarkup(savings, branch, page, query));
        });
    }

    public String buildMessage(Page<Savings> savings, int page, long startTime) {
        StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
            .append("───────────────────\n")
            .append("📄 Halaman ").append(page + 1).append(" dari ").append(savings.getTotalPages()).append("\n\n");

        savings.forEach(saving -> message.append(savingsUtils.getSavings(saving)));
        message.append("⏱️ _Eksekusi dalam ").append(System.currentTimeMillis() - startTime).append("ms_");
        return message.toString();
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
