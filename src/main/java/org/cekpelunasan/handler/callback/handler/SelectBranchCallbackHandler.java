package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class SelectBranchCallbackHandler implements CallbackProcessor {

    private final BillService billService;

    public SelectBranchCallbackHandler(BillService billService) {
        this.billService = billService;
    }

    @Override
    public String getCallBackData() {
        return "branch";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();

            String[] parts = update.getCallbackQuery().getData().split("_", 3);
            if (parts.length < 3) {
                sendMessage(update, "❌ *Data callback tidak valid*", telegramClient);
                return;
            }

            String branch = parts[1];
            String name = parts[2];
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            Page<Bills> billsPage = billService.findByNameAndBranch(name, branch, 0, 5);
            if (billsPage.isEmpty()) {
                sendMessage(update, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }

            String message = buildMessage(billsPage, start);
            InlineKeyboardMarkup markup = new ButtonListForBills().dynamicButtonName(billsPage, 0, name, branch);
            editMessageWithMarkup(chatId, update.getCallbackQuery().getMessage().getMessageId(), message, telegramClient, markup);
        });
    }

    private String buildMessage(Page<Bills> billsPage, long startTime) {
        StringBuilder message = new StringBuilder("📄 Halaman 1 dari ").append(billsPage.getTotalPages()).append("\n\n");

        billsPage.forEach(bill -> message.append("📄 *Informasi Nasabah*\n")
                .append("🔢 *No SPK*      : `").append(bill.getNoSpk()).append("`\n")
                .append("👤 *Nama*        : ").append(bill.getName()).append("\n")
                .append("🏡 *Alamat*      : ").append(bill.getAddress()).append("\n")
                .append("💰 *Plafond*     : ").append(new RupiahFormatUtils().formatRupiah(bill.getPlafond())).append("\n\n")
        );

        long elapsed = System.currentTimeMillis() - startTime;
        message.append(String.format("⏱ Eksekusi dalam %d ms", elapsed));

        return message.toString();
    }

    private void sendMessage(Update update, String text, TelegramClient telegramClient) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Gagal mengirim pesan ke chatId {}: {}", chatId, e.getMessage(), e);
        }
    }
}
