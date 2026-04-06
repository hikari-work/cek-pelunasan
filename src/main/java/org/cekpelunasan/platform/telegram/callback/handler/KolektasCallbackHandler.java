package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.core.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk paginasi data kolektas (kolektibilitas tabungan) berdasarkan kelompok.
 *
 * <p>Callback berawalan {@code "koltas"} ini menangani navigasi halaman demi halaman
 * pada hasil pencarian kolektas. Format kelompok yang valid mengikuti pola
 * {@code XXX.N} (tiga huruf, titik, diikuti angka) — misalnya {@code "KPR.1"}.
 * Jika format tidak valid, user diberi tahu agar memperbaiki inputnya.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class KolektasCallbackHandler extends AbstractCallbackHandler {

    private final KolekTasUtils kolekTasUtils;
    private final PaginationKolekTas paginationKolekTas;
    private final KolekTasService kolekTasService;

    /**
     * Mengembalikan prefix {@code "koltas"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "koltas";
    }

    /**
     * Memproses paginasi data kolektas berdasarkan kode kelompok.
     *
     * <p>Kode kelompok diambil dari index 1 data callback (huruf kecil, sudah di-trim),
     * dan nomor halaman dari index 2. Validasi dilakukan dua tahap:
     * pertama memastikan kode tidak kosong, kedua memastikan format sesuai pola regex.
     * Jika lolos validasi, data kolektas diambil dari {@code KolekTasService} dan
     * ditampilkan dengan paginasi.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Kolektas Received....");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String data = callbackData.split("_")[1].trim().toLowerCase();
        long chatId = update.chatId;
        int page = Integer.parseInt(callbackData.split("_")[2]);
        long messageId = update.messageId;

        if (data.isEmpty()) {
            log.info("Kolektas Parsing Text Is Not Successfull....");
            return Mono.fromRunnable(() -> sendMessage(chatId, "Data Tidak Boleh Kosong", client));
        }
        if (isValidKelompok(data)) {
            log.info("Group Is Not Valid");
            return Mono.fromRunnable(() -> sendMessage(chatId, "Data Tidak Valid", client));
        }

        return kolekTasService.findKolekByKelompok(data, page + 1, 5)
            .flatMap(kolek -> Mono.fromRunnable(() -> {
                StringBuilder stringBuilder = new StringBuilder();
                log.info("Sending Kolek Tas For Group {}", data);
                kolek.forEach(k -> stringBuilder.append(kolekTasUtils.buildKolekTas(k)));
                TdApi.ReplyMarkupInlineKeyboard markup = paginationKolekTas.dynamicButtonName(kolek, page, data);
                editMessageWithMarkup(chatId, messageId, stringBuilder.toString(), client, markup);
            }))
            .then();
    }

    /**
     * Memvalidasi apakah format kode kelompok sesuai pola yang diharapkan.
     *
     * <p>Pola yang valid: tepat tiga huruf, diikuti titik, lalu satu atau lebih digit.
     * Contoh valid: {@code "kpr.1"}, {@code "sme.12"}. Method ini mengembalikan
     * {@code true} apabila format TIDAK valid (sengaja dibalik untuk keperluan
     * pemeriksaan kondisi error di pemanggil).
     *
     * @param text kode kelompok yang akan divalidasi
     * @return {@code true} jika format tidak sesuai pola; {@code false} jika valid
     */
    private boolean isValidKelompok(String text) {
        return text.matches("^[a-zA-Z]{3}\\.\\d+$");
    }
}
