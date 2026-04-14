package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.DateUtils;
import org.cekpelunasan.utils.TagihanUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Handler untuk callback paginasi tagihan berdasarkan nama atau kode AO/cabang.
 *
 * <p>Dipanggil ketika user menekan tombol Next/Prev pada daftar tagihan jatuh tempo
 * yang dicari berdasarkan nama. Callback data yang ditangani berawalan {@code "pagebills"}.
 *
 * <p>Logika pemilihan query berbeda tergantung panjang kode yang dikirim:
 * <ul>
 *   <li>3 karakter — dianggap kode AO, mencari tagihan berdasarkan account officer</li>
 *   <li>4 karakter — dianggap kode cabang, mencari tagihan berdasarkan cabang dan jatuh tempo</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillsByNameCalculatorCallbackHandler extends AbstractCallbackHandler {

    private static final String CALLBACK_DATA = "pagebills";
    private static final String CALLBACK_DELIMITER = "_";
    private static final int QUERY_MIN_LENGTH = 3;
    private static final int QUERY_MAX_LENGTH = 4;
    private static final int PAGE_SIZE = 5;
    private static final String ERROR_MESSAGE = "❌ *Data tidak ditemukan*";
    private static final String HEADER_MESSAGE = "📅 *Tagihan Jatuh Bayar Hari Ini*\n\n";

    private final BillService billService;
    private final DateUtils dateUtils;
    private final PaginationBillsByNameCallbackHandler paginationBillsByNameCallbackHandler;
    private final TagihanUtils tagihanUtils;

    /**
     * Mengembalikan prefix {@code "pagebills"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return CALLBACK_DATA;
    }

    /**
     * Memproses permintaan paginasi tagihan berdasarkan kode AO atau cabang.
     *
     * <p>Alur prosesnya: parse data callback → validasi panjang query →
     * ambil halaman tagihan dari database → bangun teks pesan → edit pesan
     * yang sudah ada dengan konten baru dan tombol navigasi.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        long chatId = update.chatId;
        long messageId = update.messageId;

        CallbackData parsedData = parseCallbackData(callbackData);

        if (!isValidQuery(parsedData.query())) {
            return Mono.fromRunnable(() -> sendMessage(chatId, ERROR_MESSAGE, client));
        }

        return fetchBillsPageMono(parsedData)
            .flatMap(billsPage -> Mono.fromRunnable(() -> {
                log.info("Finding Bills By: {}", parsedData.query());
                String messageText = buildBillsMessage(billsPage);
                TdApi.ReplyMarkupInlineKeyboard markup = buildPaginationMarkup(billsPage, parsedData);
                editMessageWithMarkup(chatId, messageId, messageText, client, markup);
            }))
            .onErrorResume(e -> {
                log.error("Error processing callback", e);
                return Mono.empty();
            })
            .then();
    }

    /**
     * Memecah string data callback menjadi objek {@link CallbackData} yang terstruktur.
     *
     * @param data string mentah dari payload callback, misalnya {@code "pagebills_AO1_0"}
     * @return objek berisi query dan nomor halaman
     */
    private CallbackData parseCallbackData(String data) {
        String[] parts = data.split(CALLBACK_DELIMITER);
        return CallbackData.builder()
            .query(parts.length > 1 ? parts[1] : "")
            .page(parts.length > 2 ? Integer.parseInt(parts[2]) : 0)
            .build();
    }

    /**
     * Memvalidasi apakah panjang query sesuai dengan ketentuan (3 atau 4 karakter).
     *
     * @param query kode AO atau kode cabang yang akan dicari
     * @return {@code true} jika panjang query valid
     */
    private boolean isValidQuery(String query) {
        int length = query.length();
        return length == QUERY_MIN_LENGTH || length == QUERY_MAX_LENGTH;
    }

    /**
     * Mengambil halaman tagihan dari database sesuai jenis query.
     *
     * <p>Query 3 karakter → cari berdasarkan AO. Query 4 karakter → cari
     * berdasarkan kombinasi cabang dan status jatuh tempo hari ini.
     *
     * @param callbackData data yang sudah diparsing, berisi query dan nomor halaman
     * @return {@link Mono} berisi halaman hasil tagihan
     */
    private Mono<Page<Bills>> fetchBillsPageMono(CallbackData callbackData) {
        String query = callbackData.query();
        LocalDateTime today = LocalDateTime.now(ZoneOffset.ofHours(7));
        String convertedDate = dateUtils.converterDate(today);

        if (query.length() == QUERY_MIN_LENGTH) {
            return billService.findDueDateByAccountOfficer(query, convertedDate, callbackData.page(), PAGE_SIZE);
        } else {
            return billService.findBranchAndPayDown(query, convertedDate, callbackData.page(), PAGE_SIZE);
        }
    }

    /**
     * Menyusun teks pesan yang menampilkan daftar tagihan secara ringkas.
     *
     * @param billsPage halaman data tagihan dari database
     * @return string pesan yang siap dikirim ke Telegram
     */
    private String buildBillsMessage(Page<Bills> billsPage) {
        StringBuilder sb = new StringBuilder(HEADER_MESSAGE);
        billsPage.forEach(bills -> sb.append(tagihanUtils.billsCompact(bills)));
        return sb.toString();
    }

    /**
     * Membangun tombol paginasi inline keyboard berdasarkan status halaman saat ini.
     *
     * @param billsPage   halaman data tagihan, digunakan untuk cek ada/tidaknya halaman berikutnya
     * @param callbackData berisi query dan halaman saat ini untuk membentuk data callback tombol
     * @return objek inline keyboard dengan tombol Prev/Next yang sesuai
     */
    private TdApi.ReplyMarkupInlineKeyboard buildPaginationMarkup(Page<Bills> billsPage, CallbackData callbackData) {
        return paginationBillsByNameCallbackHandler.dynamicButtonName(
            billsPage,
            callbackData.page(),
            callbackData.query()
        );
    }

    private record CallbackData(String query, Integer page) {

        public static CallbackDataBuilder builder() {
            return new CallbackDataBuilder();
        }

        public static class CallbackDataBuilder {
            private String query;
            private Integer page;

            public CallbackDataBuilder query(String query) {
                this.query = query;
                return this;
            }

            public CallbackDataBuilder page(Integer page) {
                this.page = page;
                return this;
            }

            public CallbackData build() {
                return new CallbackData(query, page);
            }
        }
    }
}
