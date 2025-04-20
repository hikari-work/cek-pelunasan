package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.UserService;
import org.cekpelunasan.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class FindByDueDate implements CommandProcessor {
    private final UserService userService;
    private final AuthorizedChats authorizedChats;
    private final BillService billService;
    private final DateUtils dateUtils;

    public FindByDueDate(UserService userService, AuthorizedChats authorizedChats, BillService billService, DateUtils dateUtils) {
        this.userService = userService;
        this.authorizedChats = authorizedChats;
        this.billService = billService;
        this.dateUtils = dateUtils;
    }

    @Override
    public String getCommand() {
        return "/jb";
    }

    @Override
    public String getDescription() {
        return "📅 *Cek tagihan jatuh tempo hari ini*.\nGunakan command ini untuk melihat daftar tagihan Anda yang jatuh tempo hari ini.";
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.getMessage().getChatId();

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
            LocalDateTime today = LocalDateTime.now();

            Page<Bills> billsPage = null;
            if (user.getRoles() != null) {
                billsPage = switch (user.getRoles()) {
                    case AO -> billService.findDueDateByAccountOfficer(userCode, dateUtils.converterDate(today), 0, 5);
                    case PIMP -> billService.findBranchAndPayDown(userCode, dateUtils.converterDate(today), 0, 5);
                    default -> Page.empty();
                };
            }

            if (billsPage != null && billsPage.isEmpty()) {
                sendMessage(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }

            StringBuilder builder = new StringBuilder("Halaman 1 dari " + billsPage.getTotalPages() + "\n📋 *Daftar Tagihan Jatuh Tempo Hari Ini:*\n\n");
            billsPage.forEach(bills -> builder.append(messageBuilder(bills)));

            InlineKeyboardMarkup markup = new PaginationBillsByNameCallbackHandler()
                    .dynamicButtonName(billsPage, 0, userCode);

            sendMessage(chatId, builder.toString(), telegramClient, markup);
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
