package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.User;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.MinimalPayUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinimalPayCallbackHandler extends AbstractCallbackHandler {

    private final PaginationToMinimalPay paginationToMinimalPay;
    private final BillService billService;
    private final UserService userService;
    private final MinimalPayUtils minimalPayUtils;

    @Override
    public String getCallBackData() {
        return "minimal";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.chatId;
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] data = callbackData.split("_");

            int page = Integer.parseInt(data[2]);
            log.info("Bills Callback Received...");

            User user = userService.findUserByChatId(chatId).block();
            if (user == null) {
                log.info("User ID {} not Valid", chatId);
                sendMessage(chatId, "❌ *User tidak ditemukan*", client);
                return;
            }
            String userCode = user.getUserCode();

            Page<Bills> bills = null;
            if (user.getRoles() != null) {
                log.info("Finding Minimal Pay of {}", userCode);
                bills = switch (user.getRoles()) {
                    case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, page, 5).block();
                    case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, page, 5).block();
                };
            }

            if (bills != null && bills.isEmpty()) {
                log.info("Minimal Pay Is Empty....");
                sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", client);
                return;
            }

            StringBuilder message = new StringBuilder("📋 *Daftar Tagihan Minimal Bayar:*\n\n");
            if (bills != null) {
                for (Bills bill : bills) {
                    message.append(minimalPayUtils.minimalPay(bill));
                }
            }

            TdApi.ReplyMarkupInlineKeyboard markup = paginationToMinimalPay.dynamicButtonName(bills, page, userCode);
            editMessageWithMarkup(chatId, update.messageId, message.toString(), client, markup);
        });
    }
}
