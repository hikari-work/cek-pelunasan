package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler fallback untuk callback yang tidak memiliki konteks atau tidak dikenali.
 *
 * <p>Dipakai sebagai "jaring pengaman" di {@link org.cekpelunasan.platform.telegram.callback.CallbackHandler}
 * ketika tidak ada processor yang cocok dengan prefix data callback. Daripada
 * membiarkan callback menggantung tanpa respons, handler ini mengirim pop-up
 * notifikasi ke user sebagai tanda bahwa tombol tersebut memang tidak
 * menghasilkan aksi apapun.
 *
 * <p>Handler ini juga mendapat prefix {@code "noop"} yang secara eksplisit dipakai
 * oleh tombol penanda posisi halaman (misalnya "1 - 5 / 20") agar tombol tersebut
 * tidak melakukan apa-apa ketika ditekan.
 */
@Slf4j
@Component
public class NoContextCallbackHandler extends AbstractCallbackHandler {

    /**
     * Mengembalikan prefix {@code "noop"} sebagai pengenal handler fallback ini.
     */
    @Override
    public String getCallBackData() {
        return "noop";
    }

    /**
     * Mengirim pop-up alert ke user sebagai respons atas tombol yang tidak memiliki aksi.
     *
     * <p>Menggunakan {@link TdApi.AnswerCallbackQuery} dengan {@code showAlert = true}
     * agar muncul sebagai dialog pop-up, bukan sekadar notifikasi kecil di atas chat.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pop-up berhasil dikirim
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return Mono.fromRunnable(() -> {
            log.info("Someone Makes Mistakes...");
            TdApi.AnswerCallbackQuery answer = new TdApi.AnswerCallbackQuery();
            answer.callbackQueryId = update.id;
            answer.text = "🐊 Pap Dulu Dong Maniess";
            answer.showAlert = true;
            client.send(answer, r -> {});
        });
    }
}
