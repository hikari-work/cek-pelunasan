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
                        🏦 *INFORMASI KREDIT*
                        ═══════════════════
                        
                        👤 *Detail Nasabah*
                        ▢ Nama\t\t: *%s*
                        ▢ No SPK\t: `%s`
                        ▢ Alamat\t: %s
                        
                        💳 *Informasi Pinjaman*
                        ▢ Plafond\t\t: %s
                        ▢ Baki Debet\t: %s
                        ▢ Realisasi\t\t: %s
                        ▢ Jatuh Tempo\t: %s
                        
                        💹 *Angsuran*
                        ▢ Bunga\t\t: %s
                        ▢ Pokok\t\t: %s
                        ▢ Total\t\t: %s
                        
                        ⚠️ *Tunggakan*
                        ▢ Bunga\t\t: %s
                        ▢ Pokok\t\t: %s
                        
                        📊 *Status Kredit*
                        ▢ Hari Tunggakan\t: %s hari
                        ▢ Kolektibilitas\t\t: %s
                        
                        💰 *Pembayaran*
                        ▢ Total Tagihan\t\t: %s
                        
                        ⚡️ *Minimal Bayar*
                        ▢ Pokok\t\t: %s
                        ▢ Bunga\t\t: %s
                        
                        👨‍💼 *Account Officer*: %s
                        ═══════════════════
                        """,
                bill.getName(),
                bill.getNoSpk(),
                bill.getAddress(),
                formatRupiah(bill.getPlafond()),
                formatRupiah(bill.getDebitTray()),
                bill.getRealization(),
                bill.getDueDate(),
                formatRupiah(bill.getInterest()),
                formatRupiah(bill.getPrincipal()),
                formatRupiah(bill.getInstallment()),
                formatRupiah(bill.getLastInterest()),
                formatRupiah(bill.getLastPrincipal()),
                bill.getDayLate(),
                bill.getCollectStatus(),
                formatRupiah(bill.getFullPayment()),
                formatRupiah(bill.getMinPrincipal()),
                formatRupiah(bill.getMinInterest()),
                bill.getAccountOfficer()
        );
    }
    private String formatRupiah(Long amount) {
        return String.format("Rp %,d", amount);
    }
}

