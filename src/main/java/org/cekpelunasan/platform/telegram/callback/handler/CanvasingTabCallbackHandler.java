package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationCanvassingByTab;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.utils.CanvasingUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Handler untuk paginasi hasil canvasing tabungan berdasarkan filter alamat/tab.
 *
 * <p>Callback berawalan {@code "canvas"} ini dipicu ketika user menavigasi halaman
 * pada daftar tabungan yang difilter berdasarkan kata kunci alamat. Data yang
 * ditampilkan berasal dari {@code SavingsService} dan disajikan per halaman
 * dengan ukuran 5 data.
 *
 * <p>Query alamat bisa mengandung spasi maupun underscore — keduanya di-split
 * dan digabung menjadi satu daftar kata kunci sebelum diteruskan ke service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasingTabCallbackHandler extends AbstractCallbackHandler {
    private final SavingsService savingsService;
    private final PaginationCanvassingByTab paginationCanvassingByTab;
    private final CanvasingUtils canvasingUtils;

    /**
     * Mengembalikan prefix {@code "canvas"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "canvas";
    }

    /**
     * Memproses permintaan paginasi canvasing tabungan berdasarkan kata kunci alamat.
     *
     * <p>Query diambil dari bagian kedua data callback, nomor halaman dari bagian ketiga.
     * Query kemudian dipecah menjadi daftar kata kunci (split spasi dan underscore),
     * lalu digunakan untuk mencari data tabungan yang cocok. Jika kosong,
     * user mendapat notifikasi data tidak ditemukan.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Canvasing Request Process...");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] data = callbackData.split("_");
        String query = data[1];
        int page = Integer.parseInt(data[2]);
        List<String> address = Arrays.stream(query.split("_"))
            .flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
            .filter(s -> !s.isEmpty())
            .toList();

        return savingsService.findFilteredSavings(address, PageRequest.of(page, 5))
            .flatMap(savings -> Mono.fromRunnable(() -> {
                if (savings.isEmpty()) {
                    sendMessage(update.chatId, "❌ *Data tidak ditemukan*", client);
                    return;
                }
                log.info("Sending Canvasing Request....");
                StringBuilder message = new StringBuilder("📊 *INFORMASI TABUNGAN*\n")
                    .append("───────────────────\n")
                    .append(String.format("📄 Halaman %s dari ", page + 1)).append(savings.getTotalPages()).append("\n\n");
                savings.forEach(dto -> message.append(canvasingUtils.canvasingTab(dto)));
                TdApi.ReplyMarkupInlineKeyboard markup = paginationCanvassingByTab.dynamicButtonName(savings, page, query);
                editMessageWithMarkup(update.chatId, update.messageId, message.toString(), client, markup);
            }))
            .then();
    }
}
