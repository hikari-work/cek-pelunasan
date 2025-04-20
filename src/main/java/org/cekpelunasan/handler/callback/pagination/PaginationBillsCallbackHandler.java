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

    public PaginationBillsCallbackHandler(BillService billService) {
        this.billService = billService;
    }

    @Override
    public String getCallBackData() {
        return "paging";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            log.info("Data Masuk");
            long start = System.currentTimeMillis();
            String[] parts = update.getCallbackQuery().getData().split("_", 4);
            String query = parts[1];
            String branch = parts[2];
            int page = Integer.parseInt(parts[3]);
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            var messageId = update.getCallbackQuery().getMessage().getMessageId();
            Page<Bills> bills = billService.findByNameAndBranch(query, branch, page, 5);
            log.info("Page {}", page);
            log.info("Branch {}", branch);
            log.info("Query {}", query);
            if (bills.isEmpty()) {
                sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }
            StringBuilder message = new StringBuilder("📄 Halaman ").append(page + 1).append(" dari ").append(bills.getTotalPages()).append("\n\n");
            buildRepaymentList(bills, message);
            message.append("\n\n_Eksekusi dalam ").append(System.currentTimeMillis() - start).append("ms_");
            editMessageWithMarkup(chatId,
                    messageId,
                    message.toString(),
                    telegramClient,
                    new ButtonListForBills().dynamicButtonName(bills, page, query, branch));
        });
    }
    private void buildRepaymentList(Page<Bills> repayments, StringBuilder builder) {
        RupiahFormatUtils rupiahFormatter = new RupiahFormatUtils();
        repayments.forEach(dto -> builder.append("📄 *Informasi Nasabah*\n")
                .append("🔢 *No SPK*      : `").append(dto.getNoSpk()).append("`\n")
                .append("👤 *Nama*        : ").append(dto.getName()).append("\n")
                .append("🏡 *Alamat*      : ").append(dto.getAddress()).append("\n")
                .append("💰 *Plafond*     : ").append(rupiahFormatter.formatRupiah(dto.getPlafond())).append("\n\n"));
    }
}
