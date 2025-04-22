package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class BillsByNameCalculatorCallbackHandler implements CallbackProcessor {
    private final BillService billService;
    private final DateUtils dateUtils;
    private final PaginationBillsByNameCallbackHandler paginationBillsByNameCallbackHandler;

    public BillsByNameCalculatorCallbackHandler(BillService billService, DateUtils dateUtils, PaginationBillsByNameCallbackHandler paginationBillsByNameCallbackHandler) {
        this.billService = billService;
        this.dateUtils = dateUtils;
        this.paginationBillsByNameCallbackHandler = paginationBillsByNameCallbackHandler;
    }

    @Override
    public String getCallBackData() {
        return "pagebills";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            var callback = update.getCallbackQuery();
            var data = callback.getData();
            String[] parts = data.split("_");

            String query = parts[1];
            if (query.length() == 3) {
                Page<Bills> dueDateByAccountOfficer = billService.findDueDateByAccountOfficer(query, dateUtils.converterDate(LocalDateTime.now()), Integer.parseInt(parts[2]), 5);
                StringBuilder sb = new StringBuilder("📅 *Tagihan Jatuh Bayar Hari Ini*\n\n");
                dueDateByAccountOfficer.forEach(bills -> {
                    sb.append(messageBuilder(bills));
                });
                editMessageWithMarkup(callback.getMessage().getChatId(), callback.getMessage().getMessageId(), sb.toString(), telegramClient, paginationBillsByNameCallbackHandler.dynamicButtonName(dueDateByAccountOfficer, Integer.parseInt(parts[2]), query));
                return;
            }
            if (query.length() == 4) {
                Page<Bills> dueDateByAccountOfficer = billService.findBranchAndPayDown(query, dateUtils.converterDate(LocalDateTime.now()), Integer.parseInt(parts[2]), 5);
                StringBuilder sb = new StringBuilder("📅 *Tagihan Jatuh Bayar Hari Ini*\n\n");
                dueDateByAccountOfficer.forEach(bills -> {
                    sb.append(messageBuilder(bills));
                });
                editMessageWithMarkup(callback.getMessage().getChatId(), callback.getMessage().getMessageId(), sb.toString(), telegramClient, paginationBillsByNameCallbackHandler.dynamicButtonName(dueDateByAccountOfficer, Integer.parseInt(parts[2]), query));
                return;
            }
            sendMessage(callback.getMessage().getChatId(), "❌ *Data tidak ditemukan*", telegramClient);
        });
    }
    public String messageBuilder(Bills bills) {
        return "*Nama:* " + bills.getName() + "\n" +
                "• *ID SPK:* `" + bills.getNoSpk() + "`\n" +
                "• *Alamat:* " + bills.getAddress() + "\n" +
                "• *Tgl Jatuh Tempo:* " + bills.getPayDown() + "\n" +
                "• *Total Tagihan:* Rp" + String.format("%,d", bills.getFullPayment()) + ",-\n" +
                "• *AO:* " + bills.getAccountOfficer() + "\n\n";
    }
    public void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(markup)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Error");
        }
    }
}
