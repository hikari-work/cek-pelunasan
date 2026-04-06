package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationToMinimalPay;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.utils.MinimalPayUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk menampilkan daftar tagihan dengan minimal bayar yang tersisa.
 *
 * <p>Callback berawalan {@code "minimal"} ini menangani navigasi halaman pada
 * fitur minimal bayar. Data yang ditampilkan berbeda tergantung peran pengguna:
 * <ul>
 *   <li>AO — hanya melihat tagihan milik dirinya sendiri</li>
 *   <li>PIMP/ADMIN — melihat tagihan seluruh AO di bawah cabangnya</li>
 * </ul>
 *
 * <p>Identitas pengguna diambil berdasarkan {@code chatId} Telegram, sehingga
 * tidak perlu input manual dari user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinimalPayCallbackHandler extends AbstractCallbackHandler {

    private final PaginationToMinimalPay paginationToMinimalPay;
    private final BillService billService;
    private final UserService userService;
    private final MinimalPayUtils minimalPayUtils;

    /**
     * Mengembalikan prefix {@code "minimal"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "minimal";
    }

    /**
     * Memproses paginasi daftar tagihan minimal bayar sesuai peran pengguna.
     *
     * <p>Alur: identifikasi pengguna dari chatId → tentukan query yang sesuai
     * berdasarkan role (AO vs PIMP/ADMIN) → ambil data dari database →
     * tampilkan pesan dengan tombol navigasi halaman.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        long chatId = update.chatId;
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");
        int page = Integer.parseInt(data[2]);
        log.info("Bills Callback Received...");

        return userService.findUserByChatId(chatId)
            .switchIfEmpty(Mono.fromRunnable(() -> {
                log.info("User ID {} not Valid", chatId);
                sendMessage(chatId, "❌ *User tidak ditemukan*", client);
            }))
            .flatMap(user -> {
                String userCode = user.getUserCode();
                if (user.getRoles() == null) {
                    return Mono.empty();
                }
                Mono<org.springframework.data.domain.Page<org.cekpelunasan.core.entity.Bills>> billsMono =
                    switch (user.getRoles()) {
                        case AO -> billService.findMinimalPaymentByAccountOfficer(userCode, page, 5);
                        case PIMP, ADMIN -> billService.findMinimalPaymentByBranch(userCode, page, 5);
                    };
                return billsMono.flatMap(bills -> Mono.fromRunnable(() -> {
                    if (bills.isEmpty()) {
                        log.info("Minimal Pay Is Empty....");
                        sendMessage(chatId, "❌ *Tidak ada tagihan dengan minimal bayar tersisa.*", client);
                        return;
                    }
                    log.info("Finding Minimal Pay of {}", userCode);
                    StringBuilder message = new StringBuilder("📋 *Daftar Tagihan Minimal Bayar:*\n\n");
                    for (org.cekpelunasan.core.entity.Bills bill : bills) {
                        message.append(minimalPayUtils.minimalPay(bill));
                    }
                    TdApi.ReplyMarkupInlineKeyboard markup = paginationToMinimalPay.dynamicButtonName(bills, page, userCode);
                    editMessageWithMarkup(chatId, update.messageId, message.toString(), client, markup);
                }));
            })
            .then();
    }
}
