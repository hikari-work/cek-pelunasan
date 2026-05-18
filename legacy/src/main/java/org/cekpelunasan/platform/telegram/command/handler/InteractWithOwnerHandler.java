package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.utils.button.DirectMessageButton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler serbaguna untuk pesan yang tidak cocok dengan perintah manapun — sekaligus sebagai relay pesan ke owner.
 *
 * <p>Class ini menangani dua skenario utama:</p>
 * <ol>
 *   <li>User mengirim {@code /id} — bot membalas dengan Chat ID milik user tersebut,
 *       berguna saat user perlu tahu ID mereka untuk keperluan otorisasi.</li>
 *   <li>User yang sudah terotorisasi mengirim nomor rekening 12 digit — bot menampilkan
 *       tombol pilihan aksi yang bisa dilakukan terhadap nomor tersebut.</li>
 *   <li>User yang belum terotorisasi mengirim pesan apapun — pesan tersebut diteruskan
 *       ke owner bot agar owner bisa merespons secara manual.</li>
 *   <li>Owner membalas pesan yang diteruskan — balasan owner disalin kembali ke chat owner
 *       (sebagai konfirmasi), mekanisme ini bisa digunakan sebagai relay komunikasi dua arah.</li>
 * </ol>
 *
 * <p>Class ini juga berfungsi sebagai fallback handler — dipanggil ketika tidak ada perintah
 * lain yang cocok dengan teks yang dikirim user.</p>
 */
@Component
@RequiredArgsConstructor
public class InteractWithOwnerHandler extends AbstractCommandHandler {

    private final AuthorizedChats authorizedChats;
    private final DirectMessageButton directMessageButton;

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    @Override
    public String getCommand() {
        return "/id";
    }

    @Override
    public String getDescription() {
        return "Gunakan command ini untuk generate User Id anda untuk kebutuhan Authorization";
    }

    /**
     * Menangani berbagai skenario pesan berdasarkan konteks pengirim dan isi pesan.
     *
     * <p>Urutan pengecekan:</p>
     * <ol>
     *   <li>Jika teks persis {@code /id}: balas dengan Chat ID pengirim.</li>
     *   <li>Jika pengirim terotorisasi dan input mengandung 12 digit angka:
     *       tampilkan tombol aksi untuk nomor rekening tersebut.</li>
     *   <li>Jika pengirim bukan owner: teruskan pesan ke owner untuk ditindaklanjuti.</li>
     *   <li>Jika pengirim adalah owner dan sedang membalas pesan: salin balasan tersebut.</li>
     * </ol>
     *
     * @param update objek update lengkap dari Telegram, dibutuhkan untuk mengakses data pesan dan reply
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah tindakan sesuai skenario dijalankan
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return Mono.fromRunnable(() -> {
            if (!(update.message.content instanceof TdApi.MessageText messageText)) {
                return;
            }
            String text = messageText.text.text;
            long chatId = update.message.chatId;
            long messageId = update.message.id;

            if (text.equals(getCommand())) {
                sendMessage(chatId, "ID Kamu `" + chatId + "`", client);
                return;
            }

            if (authorizedChats.isAuthorized(chatId) && isValidAccount(text)) {
                sendMessage(chatId, "Pilih salah satu action dibawah ini", directMessageButton.selectServices(text.trim()), client);
                return;
            }

            if (chatId != ownerId) {
                forwardMessage(chatId, ownerId, messageId, client);
                return;
            }

            if (update.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
                copyMessage(ownerId, replyTo.messageId, ownerId, client);
            }
        });
    }

    /**
     * Memeriksa apakah input mengandung nomor rekening yang valid (12 digit angka berturutan).
     *
     * @param input teks yang akan diperiksa
     * @return {@code true} jika input mengandung 12 digit angka berturutan, {@code false} jika tidak
     */
    private boolean isValidAccount(String input) {
        Matcher matcher = Pattern.compile("\\d{12}").matcher(input);
        return matcher.find();
    }
}
