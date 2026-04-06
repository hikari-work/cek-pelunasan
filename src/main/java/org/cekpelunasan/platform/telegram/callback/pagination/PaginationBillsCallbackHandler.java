package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationBillsCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;
    private final ButtonListForBills buttonListForBills;

    @Override
    public String getCallBackData() {
        return "paging";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        long start = System.currentTimeMillis();
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 4);
        String query = parts[1];
        String branch = parts[2];
        int page = Integer.parseInt(parts[3]);
        long chatId = update.chatId;
        long messageId = update.messageId;

        return billService.findByNameAndBranch(query, branch, page, 5)
            .flatMap(bills -> Mono.fromRunnable(() -> {
                if (bills.isEmpty()) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                String message = buildBillsMessage(bills, page, start);
                var markup = buttonListForBills.dynamicButtonName(bills, page, query, branch);
                editMessageWithMarkup(chatId, messageId, message, client, markup);
            }))
            .then();
    }

    private String buildBillsMessage(Page<Bills> bills, int page, long startTime) {
        StringBuilder builder = new StringBuilder(String.format("""
            🏦 *DAFTAR NASABAH KREDIT*
            ══════════════════════
            📋 Halaman %d dari %d
            ──────────────────────

            """, page + 1, bills.getTotalPages()));

        RupiahFormatUtils formatter = new RupiahFormatUtils();
        bills.forEach(bill -> builder.append(String.format("""
                🔷 *%s*
                ┌──────────────────┐
                │ 📎 *Info Nasabah*
                │ 🆔 SPK   : `%s`
                │ 📍 Alamat: %s
                │
                │ 💰 *Info Kredit*
                │ 💎 Plafond: %s
                └──────────────────┘

                """,
            bill.getName(),
            bill.getNoSpk(),
            bill.getAddress(),
            formatter.formatRupiah(bill.getPlafond())
        )));

        builder.append("""
            ────────────────────
            ⚡️ _Tap SPK untuk menyalin_
            ⏱️ _Diproses dalam %dms_
            """.formatted(System.currentTimeMillis() - startTime));

        return builder.toString();
    }
}
