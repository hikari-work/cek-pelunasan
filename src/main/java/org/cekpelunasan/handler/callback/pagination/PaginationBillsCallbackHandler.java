package org.cekpelunasan.handler.callback.pagination;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class PaginationBillsCallbackHandler implements CallbackProcessor {
    private final BillService billService;
    private final ButtonListForBills buttonListForBills;

    public PaginationBillsCallbackHandler(BillService billService, ButtonListForBills buttonListForBills) {
        this.billService = billService;
        this.buttonListForBills = buttonListForBills;
    }

    @Override
    public String getCallBackData() {
        return "paging";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            String[] parts = update.getCallbackQuery().getData().split("_", 4);
            String query = parts[1];
            String branch = parts[2];
            int page = Integer.parseInt(parts[3]);

            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            Page<Bills> bills = billService.findByNameAndBranch(query, branch, page, 5);
            if (bills.isEmpty()) {
                sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }

            String message = buildBillsMessage(bills, page, start);
            var markup = buttonListForBills.dynamicButtonName(bills, page, query, branch);

            editMessageWithMarkup(chatId, messageId, message, telegramClient, markup);
        });
    }

    private String buildBillsMessage(Page<Bills> bills, int page, long startTime) {
        StringBuilder builder = new StringBuilder("📄 Halaman ")
                .append(page + 1).append(" dari ").append(bills.getTotalPages()).append("\n\n");

        RupiahFormatUtils formatter = new RupiahFormatUtils();
        bills.forEach(bill -> builder.append("📄 *Informasi Nasabah*\n")
                .append("🔢 *No SPK*      : `").append(bill.getNoSpk()).append("`\n")
                .append("👤 *Nama*        : ").append(bill.getName()).append("\n")
                .append("🏡 *Alamat*      : ").append(bill.getAddress()).append("\n")
                .append("💰 *Plafond*     : ").append(formatter.formatRupiah(bill.getPlafond())).append("\n\n"));

        builder.append("\n\n_Eksekusi dalam ")
                .append(System.currentTimeMillis() - startTime)
                .append("ms_");

        return builder.toString();
    }
}
