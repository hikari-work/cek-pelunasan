package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class TagihCommandHandler implements CommandProcessor {

    private final BillService billService;
    private final AuthorizedChats authorizedChats1;
    private final MessageTemplate messageTemplate;

    public TagihCommandHandler(BillService billService, AuthorizedChats authorizedChats1, MessageTemplate messageTemplate) {
        this.billService = billService;
        this.authorizedChats1 = authorizedChats1;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String getCommand() {
        return "/tagih";
    }

    @Override
    public String getDescription() {
        return """
                Mengembalikan rincian tagihan berdasarkan
                ID SPK yang anda kirimkan
                """;
    }

    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.getMessage().getChatId();
            String[] parts = update.getMessage().getText().split(" ",2);

            if (!authorizedChats1.isAuthorized(chatId)) {
                sendMessage(chatId, messageTemplate.unathorizedMessage(), telegramClient);
                return;
            }
            if (parts.length < 2) {
                sendMessage(chatId, messageTemplate.notValidDeauthFormat(), telegramClient);
                return;
            }
            long start = System.currentTimeMillis();

            try {
                String customerNumber = parts[1];
                Bills bills = billService.getBillById(customerNumber);
                if (bills == null) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                    return;
                }
                sendMessage(chatId, buildBillMessage(bills) + "\nEksekusi dalam " + (System.currentTimeMillis() - start) + " ms", telegramClient);

            } catch (Exception e) {
                log.error("Error");
            }
        });
    }
    public String buildBillMessage(Bills bill) {
        return String.format("""
            \uD83D\uDC64 *Nama:* %s
            \uD83D\uDCC5 *No SPK:* %s
            \uD83C\uDFE0 *Alamat:* %s

            💰 *Plafond:* Rp %,d
            📉 *Baki Debet:* Rp %,d
            💸 *Bunga:* Rp %,d
            💵 *Pokok:* Rp %,d
            🧾 *Angsuran:* Rp %,d

            🔻 *Minimal Pokok:* Rp %,d
            🔻 *Minimal Bunga:* Rp %,d

            👨‍💼 *Account Officer:* %s
            """,
                bill.getName(),
                bill.getNoSpk(),
                bill.getAddress(),
                bill.getPlafond(),
                bill.getDebitTray(),
                bill.getInterest(),
                bill.getPrincipal(),
                bill.getInstallment(),
                bill.getMinPrincipal(),
                bill.getMinInterest(),
                bill.getAccountOfficer()
        );
    }


}
