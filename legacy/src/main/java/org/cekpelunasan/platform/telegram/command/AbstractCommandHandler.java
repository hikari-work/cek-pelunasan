package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.service.TelegramMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * Kelas dasar yang menyediakan kemampuan pengiriman pesan untuk semua handler perintah.
 *
 * <p>Daripada setiap handler perintah menulis sendiri logika untuk mengirim teks,
 * dokumen, atau meneruskan pesan, class ini menyediakan semuanya dalam satu tempat.
 * Cukup extend class ini, dan kamu langsung bisa pakai method {@code sendMessage},
 * {@code sendDocument}, {@code copyMessage}, dan {@code forwardMessage} tanpa konfigurasi tambahan.</p>
 *
 * <p>Class ini juga mengurus ekstraksi teks dari objek update Telegram sebelum diteruskan
 * ke method {@code process(chatId, text, client)} yang lebih sederhana — jadi handler turunan
 * tidak perlu repot cast-cast objek TdApi sendiri.</p>
 */
@Slf4j
public abstract class AbstractCommandHandler implements CommandProcessor {

    @Autowired
    protected TelegramMessageService telegramMessageService;

    /**
     * Mengekstrak teks dari update Telegram dan meneruskannya ke overload {@code process} yang lebih sederhana.
     *
     * <p>Jika konten pesan bukan teks (misalnya foto atau stiker), method ini langsung
     * mengembalikan {@link Mono#empty()} tanpa memproses apa pun.</p>
     *
     * @param update objek update lengkap dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return hasil dari {@code process(chatId, text, client)}, atau {@link Mono#empty()} jika bukan pesan teks
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        if (!(update.message.content instanceof TdApi.MessageText messageText)) {
            return Mono.empty();
        }
        return process(update.message.chatId, messageText.text.text, client);
    }

    /**
     * Mengirim pesan teks biasa ke chat tertentu.
     *
     * @param chatId ID chat tujuan
     * @param text   isi pesan yang akan dikirim
     * @param client koneksi aktif ke Telegram
     */
	protected void sendMessage(long chatId, String text, SimpleTelegramClient client) {
        telegramMessageService.sendText(chatId, text, client);
    }

    /**
     * Mengirim pesan teks yang dilengkapi dengan tombol inline keyboard di bawahnya.
     *
     * @param chatId  ID chat tujuan
     * @param text    isi pesan yang akan dikirim
     * @param markup  konfigurasi tombol inline keyboard yang akan ditampilkan
     * @param client  koneksi aktif ke Telegram
     */
    protected void sendMessage(long chatId, String text, TdApi.ReplyMarkupInlineKeyboard markup, SimpleTelegramClient client) {
        telegramMessageService.sendTextWithKeyboard(chatId, text, markup, client);
    }

    /**
     * Mengirim file dokumen (misalnya PDF atau Excel) langsung ke chat tujuan.
     *
     * @param chatId   ID chat tujuan
     * @param fileName nama file yang akan ditampilkan di Telegram
     * @param bytes    isi file dalam bentuk array byte
     * @param client   koneksi aktif ke Telegram
     */
    protected void sendDocument(long chatId, String fileName, byte[] bytes, SimpleTelegramClient client) {
        telegramMessageService.sendDocument(chatId, fileName, bytes, client);
    }

    /**
     * Menyalin pesan dari satu chat ke chat lain tanpa menampilkan header "Diteruskan dari".
     *
     * <p>Berguna untuk broadcast pesan ke banyak user seolah-olah pesan dikirim langsung,
     * bukan diteruskan. Caption asli tetap dipertahankan.</p>
     *
     * @param fromChatId ID chat asal tempat pesan berada
     * @param messageId  ID pesan yang akan disalin
     * @param toChatId   ID chat tujuan tempat salinan akan dikirim
     * @param client     koneksi aktif ke Telegram
     */
    protected void copyMessage(long fromChatId, long messageId, long toChatId, SimpleTelegramClient client) {
        try {
            TdApi.ForwardMessages fwd = new TdApi.ForwardMessages();
            fwd.chatId = toChatId;
            fwd.fromChatId = fromChatId;
            fwd.messageIds = new long[]{messageId};
            fwd.sendCopy = true;
            fwd.removeCaption = false;
            client.send(fwd, result -> {
                if (result.isError()) {
                    log.error("Failed to copy message {} from {} to {}: {}", messageId, fromChatId, toChatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to copy message {}", messageId, e);
        }
    }

    /**
     * Meneruskan pesan dari satu chat ke chat lain dengan menampilkan header "Diteruskan dari".
     *
     * <p>Berbeda dengan {@link #copyMessage}, method ini mempertahankan informasi asal pesan
     * sehingga penerima tahu pesan itu diteruskan.</p>
     *
     * @param fromChatId ID chat asal tempat pesan berada
     * @param toChatId   ID chat tujuan tempat pesan akan diteruskan
     * @param messageId  ID pesan yang akan diteruskan
     * @param client     koneksi aktif ke Telegram
     */
    protected void forwardMessage(long fromChatId, long toChatId, long messageId, SimpleTelegramClient client) {
        try {
            TdApi.ForwardMessages fwd = new TdApi.ForwardMessages();
            fwd.chatId = toChatId;
            fwd.fromChatId = fromChatId;
            fwd.messageIds = new long[]{messageId};
            fwd.sendCopy = false;
            client.send(fwd, result -> {
                if (result.isError()) {
                    log.error("Failed to forward message {} from {} to {}: {}", messageId, fromChatId, toChatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to forward message {}", messageId, e);
        }
    }
}
