package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pintu masuk utama untuk semua callback query yang datang dari Telegram.
 *
 * <p>Ketika user menekan tombol inline di chat, Telegram mengirim
 * {@link TdApi.UpdateNewCallbackQuery}. Class ini bertugas membaca prefix dari
 * data callback tersebut, lalu mencari {@link CallbackProcessor} yang tepat
 * untuk menanganinya. Kalau tidak ada yang cocok, fallback ke handler "none"
 * supaya tidak ada callback yang terlewat tanpa respons.
 *
 * <p>Semua {@link CallbackProcessor} yang terdaftar sebagai Spring bean akan
 * otomatis di-inject lewat constructor dan dipetakan berdasarkan nilai
 * {@link CallbackProcessor#getCallBackData()}-nya.
 */
@Component
public class CallbackHandler {

    private final Map<String, CallbackProcessor> processorMap;

    /**
     * Mengumpulkan semua processor yang tersedia dan membangun peta prefix → processor.
     *
     * @param processorList daftar semua {@link CallbackProcessor} yang dideteksi Spring
     */
    @Autowired
    CallbackHandler(List<CallbackProcessor> processorList) {
        this.processorMap = processorList.stream()
            .collect(Collectors.toMap(CallbackProcessor::getCallBackData, c -> c));
    }

    /**
     * Menerima callback dari Telegram dan meneruskannya ke processor yang sesuai.
     *
     * <p>Metode ini berjalan secara asinkron agar tidak memblokir thread utama.
     * Prefix diambil dari bagian pertama data callback (sebelum karakter {@code _}).
     * Jika prefix tidak dikenali, handler {@code "none"} digunakan sebagai fallback.
     *
     * @param update event callback yang diterima dari Telegram
     * @param client koneksi aktif ke Telegram untuk mengirim respons
     */
    @Async
    public void handle(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        if (update.payload instanceof TdApi.CallbackQueryPayloadData payloadData) {
            String callbackData = new String(payloadData.data, StandardCharsets.UTF_8);
            String prefix = callbackData.split("_")[0];
            CallbackProcessor processor = processorMap.getOrDefault(prefix, processorMap.get("none"));
            if (processor != null) {
                processor.process(update, client).subscribe();
            }
        }
    }

}
