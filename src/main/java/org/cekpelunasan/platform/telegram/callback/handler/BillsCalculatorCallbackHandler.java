package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.TagihanUtils;
import org.cekpelunasan.utils.button.BackKeyboardButtonForBillsUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk menampilkan detail tagihan spesifik saat user menekan tombol tagihan.
 *
 * <p>Ketika user menekan tombol tagihan dari daftar (misalnya ingin melihat rincian
 * angsuran, sisa hutang, atau info debitur), callback berawalan {@code "tagihan"} ini
 * yang memproses permintaan tersebut. Handler mengambil data lengkap dari database
 * menggunakan ID yang disisipkan dalam data callback, lalu mengedit pesan yang ada
 * dengan informasi detail beserta tombol "Kembali".
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BillsCalculatorCallbackHandler extends AbstractCallbackHandler {
    private final BillService billService;
    private final TagihanUtils tagihanUtils;

    /**
     * Mengembalikan prefix {@code "tagihan"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "tagihan";
    }

    /**
     * Mengambil dan menampilkan detail tagihan berdasarkan ID yang ada di data callback.
     *
     * <p>ID tagihan diambil dari bagian kedua string callback (index 1 setelah split "_").
     * Jika ID tidak ditemukan di database, pesan error dikirim ke user. Jika berhasil,
     * pesan sebelumnya diedit dengan detail lengkap tagihan dan tombol kembali ke daftar.
     *
     * @param update event callback dari Telegram yang berisi ID tagihan
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Bills Update Received");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_", 5);
        log.info("Bill ID: {}", parts[1]);
        return billService.getBillById(parts[1])
            .switchIfEmpty(runBlocking(() -> {
                log.info("Bill ID Not Found");
                sendMessage(update.chatId, "❌ *Data tidak ditemukan*", client);
            }))
            .flatMap(bills -> runBlocking(() -> {
                log.info("Sending Bills Message");
                editMessageWithMarkup(update.chatId, update.messageId, tagihanUtils.detailBills(bills), client,
                    new BackKeyboardButtonForBillsUtils().backButton(callbackData));
            }))
            .then();
    }
}
