package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import reactor.core.publisher.Mono;

/**
 * Kontrak yang harus diimplementasi oleh setiap handler callback Telegram.
 *
 * <p>Setiap class yang menangani tombol inline di bot Telegram wajib implement
 * interface ini. {@link #getCallBackData()} dipakai sebagai kunci untuk routing
 * di {@link CallbackHandler}, sementara {@link #process} berisi logika
 * pemrosesan sebenarnya ketika tombol tersebut ditekan.
 */
public interface CallbackProcessor {

    /**
     * Mengembalikan prefix unik yang mengidentifikasi handler ini.
     *
     * <p>Nilai ini harus cocok dengan prefix data callback yang dikirim Telegram
     * (bagian sebelum karakter {@code _} pertama). Misalnya jika data callback
     * adalah {@code "tagihan_123"}, maka method ini harus mengembalikan
     * {@code "tagihan"}.
     *
     * @return string prefix sebagai pengenal handler
     */
    String getCallBackData();

    /**
     * Memproses callback yang masuk dari Telegram secara reaktif.
     *
     * @param update event callback berisi data yang dikirim user saat menekan tombol
     * @param client koneksi aktif ke Telegram untuk mengirim atau mengedit pesan
     * @return {@link Mono} yang menandakan proses selesai; nilai dalam Mono diabaikan
     */
    Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client);
}
