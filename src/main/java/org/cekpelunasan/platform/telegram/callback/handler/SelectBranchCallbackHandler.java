package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelectBranchCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;

    @Override
    public String getCallBackData() {
        return "branch";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Selecting Branch For...");
        long start = System.currentTimeMillis();
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 3);
        if (parts.length < 3) {
            log.error("Callback data Not Valid");
            return Mono.fromRunnable(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String branch = parts[1];
        String name = parts[2];
        long chatId = update.chatId;

        return billService.findByNameAndBranch(name, branch, 0, 5)
            .flatMap(billsPage -> Mono.fromRunnable(() -> {
                if (billsPage.isEmpty()) {
                    log.info("Bills Is Empty....");
                    sendMessage(update.chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                String message = buildMessage(billsPage, start);
                log.info("Sending Message Bills...");
                TdApi.ReplyMarkupInlineKeyboard markup = new ButtonListForBills().dynamicButtonName(billsPage, 0, name, branch);
                editMessageWithMarkup(chatId, update.messageId, message, client, markup);
            }))
            .then();
    }

    private String buildMessage(org.springframework.data.domain.Page<org.cekpelunasan.core.entity.Bills> billsPage, long startTime) {
        StringBuilder message = new StringBuilder("""
            🏦 *DAFTAR NASABAH*
            ══════════════════
            📋 Halaman 1 dari %d
            """.formatted(billsPage.getTotalPages()));

        billsPage.forEach(bill -> message.append("""

                🔷 *%s*
                ────────────────
                📎 *Detail Nasabah*
                ▪️ ID SPK\t\t: `%s`
                ▪️ Alamat\t\t: %s

                💰 *Informasi Kredit*
                ▪️ Plafond\t\t: %s
                ▪️ AO\t\t\t: %s
                ────────────────
                """.formatted(
                bill.getName(),
                bill.getNoSpk(),
                bill.getAddress(),
                new RupiahFormatUtils().formatRupiah(bill.getPlafond()),
                bill.getAccountOfficer()
            )
        ));

        message.append("\n⏱️ _Generated in ").append(System.currentTimeMillis() - startTime).append("ms_");

        return message.toString();
    }
}
