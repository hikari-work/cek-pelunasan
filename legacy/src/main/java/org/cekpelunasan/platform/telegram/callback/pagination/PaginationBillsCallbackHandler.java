package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.cekpelunasan.utils.button.ButtonListForBills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk navigasi halaman pada daftar tagihan nasabah kredit per cabang.
 *
 * <p>Callback berawalan {@code "paging"} ini menangani perpindahan halaman
 * pada daftar nasabah kredit yang sudah difilter berdasarkan nama dan cabang.
 * Setiap halaman menampilkan 5 data tagihan dengan detail nama, nomor SPK,
 * alamat, dan plafond kredit.
 *
 * <p>Format data callback yang diharapkan:
 * {@code "paging_<query>_<cabang>_<nomor_halaman>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationBillsCallbackHandler extends AbstractCallbackHandler {

    private final BillService billService;
    private final ButtonListForBills buttonListForBills;

    /**
     * Mengembalikan prefix {@code "paging"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "paging";
    }

    /**
     * Memuat halaman tagihan yang diminta dan memperbarui pesan dengan data terbaru.
     *
     * <p>Query nama dan kode cabang diambil dari data callback, lalu digunakan
     * untuk mengambil halaman yang sesuai dari database. Pesan diedit dengan
     * daftar nasabah yang diformat rapi beserta tombol navigasi.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diperbarui
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        long start = System.currentTimeMillis();
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 4);
        String query = parts[1];
        String branch = parts[2];
        int page = Integer.parseInt(parts[3]);
        long chatId = update.chatId;
        long messageId = update.messageId;

        return billService.findByNameAndBranch(query, branch, page, 5)
            .flatMap(bills -> Mono.fromRunnable(() -> {
                if (bills.isEmpty()) {
                    sendMessage(chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                String message = buildBillsMessage(bills, page, start);
                var markup = buttonListForBills.dynamicButtonName(bills, page, query, branch);
                editMessageWithMarkup(chatId, messageId, message, client, markup);
            }))
            .then();
    }

    /**
     * Menyusun teks pesan daftar tagihan dengan format yang konsisten dan informatif.
     *
     * <p>Setiap entri nasabah menampilkan nama, nomor SPK (dapat di-tap untuk
     * disalin), alamat, dan plafond kredit dalam format Rupiah. Di bagian
     * bawah ditampilkan waktu pemrosesan dalam milidetik.
     *
     * @param bills     halaman data tagihan dari database
     * @param page      nomor halaman saat ini (0-based)
     * @param startTime waktu mulai proses dalam milidetik untuk kalkulasi durasi
     * @return string pesan berformat Markdown yang siap dikirim ke Telegram
     */
    private String buildBillsMessage(Page<Bills> bills, int page, long startTime) {
        StringBuilder builder = new StringBuilder(String.format("""
            🏦 *DAFTAR NASABAH KREDIT*
            ══════════════════════
            📋 Halaman %d dari %d
            ──────────────────────

            """, page + 1, bills.getTotalPages()));

        RupiahFormatUtils formatter = new RupiahFormatUtils();
        bills.forEach(bill -> builder.append(String.format("""
                🔷 *%s*
                ┌──────────────────┐
                │ 📎 *Info Nasabah*
                │ 🆔 SPK   : `%s`
                │ 📍 Alamat: %s
                │
                │ 💰 *Info Kredit*
                │ 💎 Plafond: %s
                └──────────────────┘

                """,
            bill.getName(),
            bill.getNoSpk(),
            bill.getAddress(),
            formatter.formatRupiah(bill.getPlafond())
        )));

        builder.append("""
            ────────────────────
            ⚡️ _Tap SPK untuk menyalin_
            ⏱️ _Diproses dalam %dms_
            """.formatted(System.currentTimeMillis() - startTime));

        return builder.toString();
    }
}
