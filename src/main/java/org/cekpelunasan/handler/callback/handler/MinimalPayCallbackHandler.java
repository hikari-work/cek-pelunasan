package org.cekpelunasan.handler.callback.handler;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.handler.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class MinimalPayCallbackHandler implements CallbackProcessor {

    private final PaginationToMinimalPay paginationToMinimalPay;
    private final BillService billService;
    private final UserService userService;

    public MinimalPayCallbackHandler(PaginationToMinimalPay paginationToMinimalPay, BillService billService, UserService userService) {
        this.paginationToMinimalPay = paginationToMinimalPay;
        this.billService = billService;
        this.userService = userService;
    }

    @Override
    public String getCallBackData() {
        return "minimal";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String[] data = update.getCallbackQuery().getData().split("_");

            int page = Integer.parseInt(data[2]);

            Optional<User> userOpt = userService.findUserByChatId(chatId);
            if (userOpt.isEmpty()) {
                sendMessage(chatId, "❌ *User tidak ditemukan*", telegramClient);
                return;
            }

            User user = userOpt.get();
            String userCode = user.getUserCode();

            Page<Bills> bills = null;
            if (user.getRoles() != null) {
                bills = switch (user.getRoles()) {
                    case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, page, 5);
                    case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, page, 5);
                };
            }

            if (bills != null && bills.isEmpty()) {
                sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", telegramClient);
                return;
            }

            StringBuilder message = new StringBuilder("📋 *Daftar Tagihan Minimal Bayar:*\n\n");
            if (bills != null) {
                for (Bills bill : bills) {
                    message.append(String.format("""
    📑 *INFORMASI PEMBAYARAN*
    ═══════════════════════
    
    🎫 *Detail Kredit*
    ┌──────────────────┐
    │ 🔐 SPK: `%s`
    │ 👤 Nama: *%s*
    │ 🏘️ Alamat: %s
    └──────────────────┘
    
    💳 *Rincian Pembayaran*
    ┌──────────────────┐
    │ 💎 Pokok    : %s
    │ 💫 Bunga    : %s
    │
    │ 🔥 *TOTAL MINIMAL*
    │ 💰 %s
    └──────────────────┘
    
    ⚡️ *Catatan*: _Pembayaran minimal harus dilakukan sebelum jatuh bayar_
    """,
    bill.getNoSpk(),
    bill.getName(),
    bill.getAddress(),
    formatRupiah(bill.getMinPrincipal()),
    formatRupiah(bill.getMinInterest()),
    formatRupiah(bill.getMinPrincipal() + bill.getMinInterest())
));
                }
            }

            InlineKeyboardMarkup markup = paginationToMinimalPay.dynamicButtonName(bills, page, userCode);
            editMessageWithMarkup(chatId, update.getCallbackQuery().getMessage().getMessageId(), message.toString(), telegramClient, markup);
        });
    }

private String formatRupiah(long amount) {
    return String.format("Rp %,d", amount);
}
}