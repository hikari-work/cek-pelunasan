package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk menampilkan daftar tagihan nasabah setelah user memilih cabang.
 *
 * <p>Callback berawalan {@code "branch"} ini dipanggil setelah user memilih cabang
 * dari daftar pilihan saat mencari tagihan pelunasan. Handler langsung memuat
 * halaman pertama daftar nasabah kredit di cabang dan nama yang dipilih,
 * dilengkapi tombol navigasi dan informasi kredit ringkas tiap nasabah.
 *
 * <p>Format data callback yang diharapkan: {@code "branch_<kode_cabang>_<nama_nasabah>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectBranchCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;

    /**
     * Mengembalikan prefix {@code "branch"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "branch";
    }

    /**
     * Memuat daftar tagihan nasabah pada cabang yang dipilih dan menampilkannya.
     *
     * <p>Validasi format callback dilakukan terlebih dahulu — minimal harus ada
     * tiga bagian setelah split {@code "_"}. Jika kurang, pesan error dikirim.
     * Jika data ditemukan, pesan diedit dengan daftar nasabah dan tombol paginasi.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Selecting Branch For...");
        long start = System.currentTimeMillis();
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 3);
        if (parts.length < 3) {
            log.error("Callback data Not Valid");
            return runBlocking(() -> sendMessage(update.chatId, "❌ *Data callback tidak valid*", client));
        }

        String branch = parts[1];
        String name = parts[2];
        long chatId = update.chatId;

        return billService.findByNameAndBranch(name, branch, 0, 5)
            .flatMap(billsPage -> runBlocking(() -> {
                if (billsPage.isEmpty()) {
                    log.info("Bills Is Empty....");
                    sendMessage(update.chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                String message = buildMessage(billsPage, start);
                log.info("Sending Message Bills...");
                TdApi.ReplyMarkupInlineKeyboard markup = new ButtonListForBills().dynamicButtonName(billsPage, 0, name, branch);
                editMessageWithMarkup(chatId, update.messageId, message, client, markup);
            }))
            .then();
    }

    /**
     * Menyusun teks pesan daftar nasabah kredit dengan detail ringkas setiap nasabah.
     *
     * <p>Setiap entri menampilkan nama, nomor SPK, alamat, plafond kredit, dan kode AO.
     * Di bagian bawah ditambahkan waktu pemrosesan dalam milidetik.
     *
     * @param billsPage halaman data tagihan dari database
     * @param startTime waktu mulai proses dalam milidetik untuk kalkulasi durasi
     * @return string pesan berformat Markdown yang siap dikirim ke Telegram
     */
    private String buildMessage(org.springframework.data.domain.Page<org.cekpelunasan.core.entity.Bills> billsPage, long startTime) {
        StringBuilder message = new StringBuilder("""
            🏦 *DAFTAR NASABAH*
            ══════════════════
            📋 Halaman 1 dari %d
            """.formatted(billsPage.getTotalPages()));

        billsPage.forEach(bill -> message.append("""

                🔷 *%s*
                ────────────────
                📎 *Detail Nasabah*
                ▪️ ID SPK\t\t: `%s`
                ▪️ Alamat\t\t: %s

                💰 *Informasi Kredit*
                ▪️ Plafond\t\t: %s
                ▪️ AO\t\t\t: %s
                ────────────────
                """.formatted(
                bill.getName(),
                bill.getNoSpk(),
                bill.getAddress(),
                new RupiahFormatUtils().formatRupiah(bill.getPlafond()),
                bill.getAccountOfficer()
            )
        ));

        message.append("\n⏱️ _Generated in ").append(System.currentTimeMillis() - startTime).append("ms_");

        return message.toString();
    }
}
