package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.utils.button.BackKeyboardButtonForBillsUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class BillsCalculatorCallbackHandler implements CallbackProcessor {
    private final BillService billService;

    public BillsCalculatorCallbackHandler(BillService billService) {
        this.billService = billService;
    }

    @Override
    public String getCallBackData() {
        return "tagihan";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        CompletableFuture.runAsync(() -> {
            log.info("Update Received");
           String[] parts = update.getCallbackQuery().getData().split("_", 5);
           log.info("Parts: {}", parts.length);
           Bills bills = billService.getBillById(parts[1]);
           log.info("Bill ID: {}", parts[1]);
           if (bills == null) {
               sendMessage(update.getMessage().getChatId(), "❌ *Data tidak ditemukan*", telegramClient);
               return;
           }
           log.info("Send Message");
           log.info("Data {}", update.getCallbackQuery().getData());
           editMessageWithMarkup(update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), buildBillMessage(bills), telegramClient, new BackKeyboardButtonForBillsUtils().backButton(update.getCallbackQuery().getData()));
        });
        return CompletableFuture.completedFuture(null);
    }
    public String buildBillMessage(Bills bill) {
        return String.format("""
            \uD83D\uDC64 *Nama:* %s
            \uD83D\uDCC5 *No SPK:* %s
            \uD83C\uDFE0 *Alamat:* %s

            💰 *Plafond\t\t\t\t:* Rp %,d
            📉 *Baki Debet\t\t:* Rp %,d
            🗓️ *Realisasi:* %s
            🗓️ *Jatuh Tempo:* %s
            
            💸 *Bunga:* Rp %,d
            💵 *Pokok:* Rp %,d
            🧾 *Angsuran:* Rp %,d
            
            📅 *OD:* %s
            📅 *Kolektibilitas:* %s
            
            🧾 *Total Bayar :* Rp %,d

            🔻 *Minimal Pokok:* Rp %,d
            🔻 *Minimal Bunga:* Rp %,d

            👨‍💼 *Account Officer:* %s
            """,
                bill.getName(),
                bill.getNoSpk(),
                bill.getAddress(),
                bill.getPlafond(),
                bill.getDebitTray(),
                bill.getRealization(),
                bill.getDueDate(),
                bill.getInterest(),
                bill.getPrincipal(),
                bill.getInstallment(),
                bill.getDayLate(),
                bill.getCollectStatus(),
                bill.getFullPayment(),
                bill.getMinPrincipal(),
                bill.getMinInterest(),
                bill.getAccountOfficer()
        );
    }
}
