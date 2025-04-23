package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class MinimalPayCommand implements CommandProcessor {

    private final AuthorizedChats authorizedChats;
    private final UserService userService;
    private final BillService billService;
    private final PaginationToMinimalPay paginationToMinimalPay;

    public MinimalPayCommand(
            AuthorizedChats authorizedChats,
            UserService userService,
            BillService billService,
            PaginationToMinimalPay paginationToMinimalPay) {
        this.authorizedChats = authorizedChats;
        this.userService = userService;
        this.billService = billService;
        this.paginationToMinimalPay = paginationToMinimalPay;
    }

    @Override
    public String getCommand() {
        return "/pabpr";
    }

    @Override
    public String getDescription() {
        return """
                Menampilkan daftar tagihan yang masih memiliki minimal bayar tersisa.
                """;
    }

    @Override
    public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {

            if (!authorizedChats.isAuthorized(chatId)) {
                sendMessage(chatId, "🚫 Anda tidak memiliki akses untuk menggunakan command ini.", telegramClient);
                return;
            }

            Optional<User> userOpt = userService.findUserByChatId(chatId);
            if (userOpt.isEmpty()) {
                sendMessage(chatId, "❌ *User tidak ditemukan*", telegramClient);
                return;
            }

            User user = userOpt.get();
            String userCode = user.getUserCode();
            Page<Bills> bills;

            if (user.getRoles() != null) {
                bills = switch (user.getRoles()) {
                    case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, 0, 5);
                    case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, 0, 5);
                };

                if (bills == null || bills.isEmpty()) {
                    sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", telegramClient);
                    return;
                }

                StringBuilder message = new StringBuilder("📋 *Daftar Tagihan Minimal Bayar:*\n\n");
                for (Bills bill : bills) {
                    message.append("🔹 *No. SPK*: ").append("`").append(bill.getNoSpk()).append("`").append("\n")
                            .append("👤 *Nama*: ").append(bill.getName()).append("\n")
                            .append("🏠 *Alamat*: ").append(bill.getAddress()).append("\n")
                            .append("💵 *Pokok*: Rp ").append(String.format("%,d", bill.getMinPrincipal())).append("\n")
                            .append("💰 *Bunga*: Rp ").append(String.format("%,d", bill.getMinInterest())).append("\n")
                            .append("🧾 *Total Minimal Bayar*: Rp ")
                            .append(String.format("%,d", bill.getMinPrincipal() + bill.getMinInterest())).append("\n\n");
                }
                InlineKeyboardMarkup markup;
                markup = paginationToMinimalPay
                        .dynamicButtonName(bills, 0, userCode);

                sendMessage(chatId, message.toString(), telegramClient, markup);
            }
        });
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
