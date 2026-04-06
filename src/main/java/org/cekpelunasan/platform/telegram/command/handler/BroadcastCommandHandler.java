package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.users.UserService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /broadcast} — menyebarkan pesan ke semua user terdaftar.
 *
 * <p>Cara penggunaannya: balas (reply) pesan yang ingin disebarkan, lalu ketik {@code /broadcast}.
 * Bot akan mengirimkan salinan pesan tersebut ke seluruh user yang ada di database,
 * dengan jeda 500ms di antara setiap pengiriman agar tidak terkena rate limit Telegram.</p>
 *
 * <p>Pesan yang dikirim tidak menampilkan header "Diteruskan dari" — terlihat seperti
 * pesan baru yang dikirim langsung oleh bot ke masing-masing user.</p>
 *
 * <p>Hanya admin yang dapat menjalankan perintah ini.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastCommandHandler extends AbstractCommandHandler {

    private static final long DELAY_BETWEEN_USERS_MS = 500;

    private final UserService userService;

    @Override
    public String getCommand() {
        return "/broadcast";
    }

    @Override
    public String getDescription() {
        return """
            Kirim pesan ke semua user terdaftar.
            Format: /broadcast <pesan>
            """;
    }

    /**
     * Mengambil pesan yang di-reply, lalu menyalinnya ke semua user terdaftar satu per satu.
     *
     * <p>Jika perintah dikirim tanpa me-reply pesan apapun, bot akan membalas dengan instruksi
     * cara penggunaan yang benar. Setelah broadcast selesai, admin mendapat laporan berapa
     * banyak user yang berhasil menerima pesan.</p>
     *
     * @param update objek update lengkap dari Telegram, dibutuhkan untuk mengakses data reply
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah semua user menerima pesan atau jika terjadi error
     */
    @Override
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        long chatId = update.message.chatId;

        if (update.message.replyTo == null) {
            return Mono.fromRunnable(() ->
                sendMessage(chatId, "❗ *Format salah.*\nBalas pesan yang mau di-broadcast, lalu ketik `/broadcast`", client));
        }

        long replyMessageId = ((TdApi.MessageReplyToMessage) update.message.replyTo).messageId;

        return userService.findAllUsers()
            .collectList()
            .flatMap(allUsers -> Mono.fromRunnable(() -> {
                for (var user : allUsers) {
                    log.info("Copying To {}", user.getChatId());
                    copyMessage(chatId, replyMessageId, user.getChatId(), client);
                    try {
                        Thread.sleep(DELAY_BETWEEN_USERS_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted saat delay antar user", e);
                    }
                }
                sendMessage(chatId, "✅ Broadcast copyMessage selesai ke " + allUsers.size() + " pengguna.", client);
            }))
            .onErrorResume(e -> {
                log.error("Gagal broadcast copyMessage", e);
                return Mono.fromRunnable(() -> sendMessage(chatId, "❗ Gagal melakukan broadcast salinan pesan.", client));
            })
            .then();
    }
}
