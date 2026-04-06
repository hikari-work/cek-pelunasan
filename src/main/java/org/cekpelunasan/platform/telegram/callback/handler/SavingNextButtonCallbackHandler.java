package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationSavingsButton;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.SavingsUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Handler untuk navigasi halaman berikutnya/sebelumnya pada daftar tabungan nasabah.
 *
 * <p>Callback berawalan {@code "tab"} ini dipanggil ketika user menekan tombol
 * Next atau Prev pada hasil pencarian tabungan yang sudah difilter berdasarkan
 * nama dan cabang. Setiap halaman menampilkan 5 data tabungan.
 *
 * <p>Format data callback yang diharapkan: {@code "tab_<query>_<cabang>_<halaman>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavingNextButtonCallbackHandler extends AbstractCallbackHandler {

    private final SavingsService savingsService;
    private final PaginationSavingsButton paginationSavingsButton;
    private final SavingsUtils savingsUtils;

    /**
     * Mengembalikan prefix {@code "tab"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "tab";
    }

    /**
     * Memuat halaman tabungan yang diminta dan memperbarui pesan Telegram.
     *
     * <p>Query nama dan kode cabang diambil dari data callback, lalu digunakan
     * untuk mencari data tabungan di halaman yang sesuai. Pesan lama diedit
     * dengan data terbaru beserta tombol paginasi.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diperbarui
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Generating Saving Data....");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_");
        String query = parts[1];
        String branch = parts[2];
        int page = Integer.parseInt(parts[3]);
        long chatId = update.chatId;
        long messageId = update.messageId;

        return savingsService.findByNameAndBranch(query, branch, page)
            .flatMap(savings -> Mono.fromRunnable(() -> {
                if (savings.isEmpty()) {
                    log.info("Saving data Updated...");
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                String message = buildMessage(savings, page, System.currentTimeMillis());
                editMessageWithMarkup(chatId, messageId, message, client, paginationSavingsButton.keyboardMarkup(savings, branch, page, query));
            }))
            .then();
    }

    /**
     * Menyusun teks pesan daftar tabungan dengan informasi halaman dan waktu eksekusi.
     *
     * @param savings   halaman data tabungan yang akan ditampilkan
     * @param page      nomor halaman saat ini (0-based)
     * @param startTime waktu mulai proses dalam milidetik, digunakan untuk menghitung durasi
     * @return string pesan yang siap dikirim ke Telegram
     */
    public String buildMessage(Page<Savings> savings, int page, long startTime) {
        StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
            .append("───────────────────\n")
            .append("📄 Halaman ").append(page + 1).append(" dari ").append(savings.getTotalPages()).append("\n\n");

        savings.forEach(saving -> message.append(savingsUtils.getSavings(saving)));
        message.append("⏱️ _Eksekusi dalam ").append(System.currentTimeMillis() - startTime).append("ms_");
        return message.toString();
    }

    /**
     * Memformat angka nominal menjadi format Rupiah dengan pemisah ribuan titik.
     *
     * <p>Contoh: {@code 1500000} menjadi {@code "Rp1.500.000"}.
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
