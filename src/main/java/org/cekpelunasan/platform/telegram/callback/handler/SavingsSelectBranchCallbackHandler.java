package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

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
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Selecting Branch For Savings");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");
        String branchName = data[1];
        String query = data[2];
        long chatId = update.chatId;
        long messageId = update.messageId;

        return savingsService.findByNameAndBranch(query, branchName, 0)
            .flatMap(savings -> Mono.fromRunnable(() -> {
                if (savings.isEmpty()) {
                    log.info("Branch Tab is Not Found...");
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                log.info("Sending Savings Data");
                TdApi.ReplyMarkupInlineKeyboard markup = paginationSavingsButton.keyboardMarkup(savings, branchName, 0, query);
                editMessageWithMarkup(chatId, messageId, savingsUtils.buildMessage(savings, 0, System.currentTimeMillis()), client, markup);
            }))
            .then();
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
