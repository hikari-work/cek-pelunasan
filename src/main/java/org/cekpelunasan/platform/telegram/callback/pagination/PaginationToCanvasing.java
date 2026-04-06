package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.credithistory.CreditHistoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Handler untuk paginasi hasil canvasing riwayat kredit berdasarkan kata kunci alamat.
 *
 * <p>Callback berawalan {@code "canvasing"} ini menangani perpindahan halaman
 * pada hasil pencarian canvasing dari data riwayat kredit. Berbeda dengan
 * {@link org.cekpelunasan.platform.telegram.callback.handler.CanvasingTabCallbackHandler}
 * yang mencari dari data tabungan, handler ini menggunakan {@code CreditHistoryService}
 * untuk mencari nasabah berdasarkan alamat.
 *
 * <p>Format data callback yang diharapkan: {@code "canvasing_<alamat>_<nomor_halaman>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationToCanvasing extends AbstractCallbackHandler {

    private final CreditHistoryService creditHistoryService;
    private final PaginationCanvassingButton paginationCanvassingButton;

    /**
     * Mengembalikan prefix {@code "canvasing"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "canvasing";
    }

    /**
     * Memuat halaman canvasing yang diminta dan menampilkan data nasabah dengan alamat terkait.
     *
     * <p>Kata kunci alamat dari data callback dipecah berdasarkan spasi menjadi
     * daftar, lalu diteruskan ke service untuk pencarian full-text. Setiap entri
     * menampilkan nama, ID CIF, alamat (dipotong jika terlalu panjang), dan
     * nomor telepon nasabah.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");
        String address = data[1];
        long chatId = update.chatId;
        int page = Integer.parseInt(data[2]);
        List<String> addressList = Arrays.asList(address.split(" "));
        log.info("Data = {}", callbackData);

        return creditHistoryService.searchAddressByKeywords(addressList, page)
            .flatMap(creditHistoriesPage -> Mono.fromRunnable(() -> {
                if (creditHistoriesPage.isEmpty()) {
                    sendMessage(chatId, String.format("Data dengan alamat %s Tidak Ditemukan\n", address), client);
                    log.info("Data Empty");
                    return;
                }
                StringBuilder messageBuilder = new StringBuilder(String.format("📄 Halaman " + (page + 1) + " dari %d\n\n", creditHistoriesPage.getTotalPages()));
                creditHistoriesPage.forEach(dto -> messageBuilder.append(String.format("""
                        👤 *%s*
                        ╔═══════════════════════
                        ║ 📊 *DATA NASABAH*
                        ║ ├─── 🆔 CIF   : `%s`
                        ║ ├─── 📍 Alamat : %s
                        ║ └─── 📱 Kontak : %s
                        ╚═══════════════════════

                        """,
                    dto.getName(),
                    dto.getCustomerId(),
                    formatAddress(dto.getAddress()),
                    formatPhone(dto.getPhone())
                )));

                TdApi.ReplyMarkupInlineKeyboard markup = paginationCanvassingButton.dynamicButtonName(creditHistoriesPage, page, address);
                editMessageWithMarkup(chatId, update.messageId, messageBuilder.toString(), client, markup);
            }))
            .then();
    }

    /**
     * Memotong teks alamat jika terlalu panjang agar pesan tidak terlalu lebar.
     *
     * <p>Alamat yang melebihi 40 karakter akan dipotong dan diberi tanda "..."
     * di akhir. Ini menjaga tampilan pesan tetap rapi di layar mobile.
     *
     * @param address teks alamat asli dari database
     * @return alamat yang sudah dipotong jika perlu, atau teks asli jika masih dalam batas
     */
    private String formatAddress(String address) {
        return address.length() > 40 ? address.substring(0, 37) + "..." : address;
    }

    /**
     * Memformat nomor telepon menjadi format yang konsisten dengan ikon telepon.
     *
     * <p>Nomor yang tidak diawali "0" akan otomatis ditambahkan "0" di depannya.
     * Jika nomor kosong atau null, ditampilkan tanda tidak tersedia.
     *
     * @param phone nomor telepon dari database (bisa null atau kosong)
     * @return string nomor telepon berformat dengan ikon, atau keterangan tidak tersedia
     */
    private String formatPhone(String phone) {
        return phone == null || phone.isEmpty() ? "📵 Tidak tersedia" :
            phone.startsWith("0") ? "☎️ " + phone : "☎️ 0" + phone;
    }
}
