package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MessageTemplate;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /auth} — memberikan akses bot kepada user baru.
 *
 * <p>Hanya admin yang bisa menjalankan perintah ini. Caranya dengan mengirim
 * {@code /auth <chat_id>} di mana {@code chat_id} adalah ID Telegram dari user
 * yang ingin diberi akses. Setelah berhasil, user tersebut langsung mendapat
 * notifikasi bahwa akses mereka sudah aktif, dan owner bot juga diberitahu.</p>
 *
 * <p>Class ini menyimpan ID yang sudah diotorisasi ke dalam {@link AuthorizedChats}
 * (cache in-memory) sekaligus menyimpannya secara permanen ke database melalui {@link UserService}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCommandHandler extends AbstractCommandHandler {

    private final AuthorizedChats authorizedChats;
    private final UserService userService;
    private final MessageTemplate messageTemplate;

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    @Override
    public String getCommand() {
        return "/auth";
    }

    @Override
    public String getDescription() {
        return "Gunakan command ini untuk memberikan izin kepada user untuk menggunakan bot.";
    }

    /**
     * Memeriksa bahwa pengirim perintah adalah admin sebelum memproses otorisasi.
     *
     * @param update objek update dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return hasil proses otorisasi, atau ditolak jika bukan admin
     */
    @Override
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    /**
     * Memproses permintaan otorisasi dengan menambahkan user ke daftar yang diizinkan.
     *
     * <p>Perintah yang valid adalah {@code /auth <chat_id>} dengan {@code chat_id} berupa angka.
     * Jika format tidak sesuai atau bukan angka, bot akan membalas dengan pesan error yang sesuai.
     * Jika berhasil, user target mendapat notifikasi otorisasi dan owner bot mendapat konfirmasi.</p>
     *
     * @param chatId ID chat admin yang mengirim perintah
     * @param text   teks lengkap perintah yang dikirim, termasuk {@code chat_id} target
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah proses otorisasi berhasil atau gagal dengan pesan error
     */
    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String[] parts = text.split(" ");
        if (parts.length < 2) {
            return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidDeauthFormat(), client));
        }
        long target;
        try {
            target = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Mono.fromRunnable(() -> sendMessage(chatId, messageTemplate.notValidNumber(), client));
        }
        log.info("Trying Auth {}", target);
        return userService.insertNewUsers(target)
            .doOnSuccess(v -> {
                authorizedChats.addAuthorizedChat(target);
                sendMessage(target, messageTemplate.authorizedMessage(), client);
                log.info("Success Auth {}", target);
                sendMessage(ownerId, "Sukses", client);
            });
    }
}
