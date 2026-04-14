package org.cekpelunasan.platform.telegram.bot;

import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.CallbackHandler;
import org.cekpelunasan.platform.telegram.command.CommandHandler;
import org.cekpelunasan.platform.telegram.service.MessageIdResolver;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Pintu masuk utama bot Telegram ke aplikasi ini.
 *
 * <p>Class ini bertanggung jawab menghidupkan koneksi ke Telegram menggunakan library TDLight,
 * lalu mendelegasikan setiap pesan teks yang masuk ke {@link CommandHandler} dan setiap
 * klik tombol inline ke {@link CallbackHandler}. Singkatnya, kalau ada user yang kirim
 * pesan atau tekan tombol di chat, semua bermula dari sini.</p>
 *
 * <p>Bot mulai berjalan otomatis begitu aplikasi Spring siap (event {@code ApplicationReadyEvent}),
 * dan akan menutup koneksi dengan rapi saat aplikasi dimatikan.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot implements ApplicationListener<ApplicationReadyEvent> {

    private final SimpleTelegramClientFactory factory;
    private final TDLibSettings settings;
    private final CommandHandler commandHandler;
    private final CallbackHandler callbackHandler;
    private final MessageIdResolver messageIdResolver;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Getter
	private SimpleTelegramClient client;

    /**
     * Dijalankan otomatis oleh Spring begitu seluruh aplikasi sudah siap.
     *
     * <p>Method ini membangun koneksi TDLight, mendaftarkan listener untuk pesan baru
     * dan callback query, lalu mengotentikasi bot menggunakan token yang ada di konfigurasi.</p>
     *
     * @param event event yang diterima dari Spring saat aplikasi sudah fully started
     */
    @Override
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        try {
            log.info("Starting TDLight client...");
            SimpleTelegramClientBuilder builder = factory.builder(settings);
            builder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onMessage);
            builder.addUpdateHandler(TdApi.UpdateNewCallbackQuery.class, this::onCallbackQuery);
            builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, update ->
                log.info("TDLight auth state: {}", update.authorizationState.getClass().getSimpleName()));
            builder.addUpdateHandler(TdApi.UpdateMessageSendSucceeded.class, update ->
                messageIdResolver.resolve(update.oldMessageId, update.message.id));
            client = builder.build(AuthenticationSupplier.bot(botToken));
            log.info("TDLight client started.");
        } catch (Exception e) {
            log.error("Failed to start TDLight client", e);
        }
    }

    /**
     * Menerima update pesan baru dari Telegram dan meneruskannya ke {@link CommandHandler}.
     *
     * <p>Hanya pesan dengan tipe {@link TdApi.MessageText} yang diproses lebih lanjut —
     * pesan foto, stiker, atau tipe lain diabaikan di sini.</p>
     *
     * @param update objek update yang berisi detail pesan yang baru masuk
     */
    private void onMessage(TdApi.UpdateNewMessage update) {
        if (update.message.content instanceof TdApi.MessageText) {
            commandHandler.handle(update, client);
        }
    }

    /**
     * Menerima callback dari tombol inline yang ditekan user, lalu meneruskannya ke {@link CallbackHandler}.
     *
     * @param update objek update yang berisi data callback dari tombol yang ditekan
     */
    private void onCallbackQuery(TdApi.UpdateNewCallbackQuery update) {
        callbackHandler.handle(update, client);
    }

    /**
     * Menutup koneksi TDLight dengan bersih saat aplikasi Spring dimatikan.
     *
     * <p>Dipanggil otomatis oleh Spring sebelum bean dihancurkan, sehingga koneksi
     * ke Telegram tidak dibiarkan menggantung begitu saja.</p>
     */
	@PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
                log.info("TDLight client closed.");
            } catch (Exception e) {
                log.warn("Error closing TDLight client", e);
            }
        }
    }
}
