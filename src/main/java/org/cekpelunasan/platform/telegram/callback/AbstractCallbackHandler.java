package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Kelas induk untuk semua handler callback Telegram.
 *
 * <p>Daripada setiap handler menulis ulang cara kirim pesan atau edit pesan,
 * cukup extend class ini dan pakai method {@link #sendMessage} atau
 * {@link #editMessageWithMarkup} yang sudah tersedia. Semua handler callback
 * di aplikasi ini mewarisi dari sini.
 */
@Slf4j
public abstract class AbstractCallbackHandler implements CallbackProcessor {

    @Autowired
    protected TelegramMessageService telegramMessageService;

    /**
     * Mengirim pesan teks biasa ke chat Telegram.
     *
     * @param chatId ID chat tujuan
     * @param text   isi pesan yang akan dikirim (mendukung Markdown)
     * @param client koneksi aktif ke Telegram
     */
    protected void sendMessage(long chatId, String text, SimpleTelegramClient client) {
        telegramMessageService.sendText(chatId, text, client);
    }

    /**
     * Mengedit pesan yang sudah ada dan mengganti inline keyboard-nya sekaligus.
     *
     * <p>Biasanya dipanggil ketika user menekan tombol navigasi (misalnya Next/Prev),
     * sehingga pesan yang sama diperbarui dengan konten halaman baru beserta tombol
     * paginasi yang sudah diupdate.
     *
     * @param chatId    ID chat tempat pesan berada
     * @param messageId ID pesan yang akan diedit
     * @param text      teks baru pengganti teks lama
     * @param client    koneksi aktif ke Telegram
     * @param markup    susunan tombol inline keyboard yang baru
     */
    protected void editMessageWithMarkup(long chatId, long messageId, String text, SimpleTelegramClient client, TdApi.ReplyMarkupInlineKeyboard markup) {
        telegramMessageService.editMessageWithMarkup(chatId, messageId, text, markup, client);
    }
}
