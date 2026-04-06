package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Handler untuk menampilkan halaman pertama tabungan setelah user memilih cabang.
 *
 * <p>Callback berawalan {@code "branchtab"} ini dipanggil tepat setelah user
 * memilih cabang dari daftar pilihan cabang. Handler langsung memuat halaman
 * pertama (page 0) dari data tabungan nasabah di cabang yang dipilih, difilter
 * berdasarkan nama yang sebelumnya dicari.
 *
 * <p>Format data callback yang diharapkan: {@code "branchtab_<kode_cabang>_<query>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsSelectBranchCallbackHandler extends AbstractCallbackHandler {

    private final SavingsService savingsService;
    private final PaginationSavingsButton paginationSavingsButton;
    private final SavingsUtils savingsUtils;

    /**
     * Mengembalikan prefix {@code "branchtab"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "branchtab";
    }

    /**
     * Memuat dan menampilkan halaman pertama tabungan untuk cabang yang dipilih user.
     *
     * <p>Kode cabang diambil dari index 1 data callback, query nama nasabah dari
     * index 2. Halaman dimulai dari 0. Jika tidak ada data yang cocok,
     * user mendapat notifikasi data tidak ditemukan.
     *
     * @param update event callback dari Telegram yang berisi pilihan cabang user
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Selecting Branch For Savings");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");
        String branchName = data[1];
        String query = data[2];
        long chatId = update.chatId;
        long messageId = update.messageId;

        return savingsService.findByNameAndBranch(query, branchName, 0)
            .flatMap(savings -> Mono.fromRunnable(() -> {
                if (savings.isEmpty()) {
                    log.info("Branch Tab is Not Found...");
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                log.info("Sending Savings Data");
                TdApi.ReplyMarkupInlineKeyboard markup = paginationSavingsButton.keyboardMarkup(savings, branchName, 0, query);
                editMessageWithMarkup(chatId, messageId, savingsUtils.buildMessage(savings, 0, System.currentTimeMillis()), client, markup);
            }))
            .then();
    }

    /**
     * Memformat angka nominal menjadi format Rupiah dengan pemisah ribuan titik.
     *
     * <p>Contoh: {@code 2000000} menjadi {@code "Rp2.000.000"}.
     *
     * @param amount nominal dalam bentuk Long; jika null mengembalikan {@code "Rp0"}
     * @return string nominal dalam format Rupiah
     */
    public String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }
}
