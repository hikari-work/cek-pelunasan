package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.SelectSavingsBranch;
import org.cekpelunasan.utils.button.ButtonListForSelectBranch;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk menampilkan pilihan cabang sesuai layanan yang dipilih user.
 *
 * <p>Callback berawalan {@code "services"} ini menjadi jembatan antara hasil
 * pencarian nama nasabah dan pemilihan cabang. Berdasarkan jenis layanan yang
 * dipilih ({@code "Pelunasan"} atau {@code "Tabungan"}), handler mengambil
 * daftar cabang yang tersedia dan menampilkannya sebagai tombol pilihan.
 *
 * <p>Format data callback yang diharapkan: {@code "services_<jenis_layanan>_<nama_nasabah>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServicesCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;
    private final SavingsService savingsService;
    private final ButtonListForSelectBranch buttonListForSelectBranch;
    private final SelectSavingsBranch selectSavingsBranch;

    /**
     * Mengembalikan prefix {@code "services"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "services";
    }

    /**
     * Menentukan jenis layanan dari data callback dan mengarahkan ke handler yang sesuai.
     *
     * <p>Validasi format callback dilakukan lebih dulu. Jika layanan adalah
     * {@code "Pelunasan"}, daftar cabang diambil dari data tagihan. Jika
     * {@code "Tabungan"}, daftar cabang diambil dari data tabungan berdasarkan
     * query nama nasabah. Layanan yang tidak dikenali mendapat pesan error.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit dengan pilihan cabang
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 3);
        if (parts.length < 3) {
            log.error("Callback data not valid: {}", callbackData);
            return runBlocking(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String service = parts[1];
        String query = parts[2];
        long chatId = update.chatId;
        long messageId = update.messageId;

        log.info("Services callback: service={}, query={}", service, query);

        return switch (service) {
            case "Pelunasan" -> handlePelunasan(chatId, messageId, query, client);
            case "Tabungan" -> handleTabungan(chatId, messageId, query, client);
            default -> {
                log.warn("Unknown service: {}", service);
                yield runBlocking(() -> sendMessage(chatId, "❌ *Layanan tidak dikenali*", client));
            }
        };
    }

    /**
     * Menampilkan daftar pilihan cabang untuk layanan pelunasan kredit.
     *
     * <p>Mengambil semua cabang yang memiliki data tagihan aktif, lalu
     * menampilkannya sebagai tombol inline untuk dipilih user.
     *
     * @param chatId    ID chat tujuan
     * @param messageId ID pesan yang akan diedit
     * @param query     nama nasabah yang dicari
     * @param client    koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    private Mono<Void> handlePelunasan(long chatId, long messageId, String query, SimpleTelegramClient client) {
        return billService.lisAllBranch()
            .flatMap(branches -> runBlocking(() -> {
                if (branches.isEmpty()) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                editMessageWithMarkup(chatId, messageId,
                    "🏦 *Pilih Cabang untuk Pelunasan*\n\nNasabah: *" + query + "*",
                    client,
                    buttonListForSelectBranch.dynamicSelectBranch(branches, query));
            }))
            .then();
    }

    /**
     * Menampilkan daftar pilihan cabang untuk layanan tabungan.
     *
     * <p>Mengambil cabang-cabang yang memiliki rekening tabungan atas nama
     * nasabah yang dicari, lalu menampilkannya sebagai tombol pilihan.
     *
     * @param chatId    ID chat tujuan
     * @param messageId ID pesan yang akan diedit
     * @param query     nama nasabah yang dicari
     * @param client    koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    private Mono<Void> handleTabungan(long chatId, long messageId, String query, SimpleTelegramClient client) {
        return savingsService.listAllBranch(query)
            .flatMap(branches -> runBlocking(() -> {
                if (branches.isEmpty()) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                editMessageWithMarkup(chatId, messageId,
                    "💰 *Pilih Cabang untuk Tabungan*\n\nNasabah: *" + query + "*",
                    client,
                    selectSavingsBranch.dynamicSelectBranch(branches, query));
            }))
            .then();
    }
}
