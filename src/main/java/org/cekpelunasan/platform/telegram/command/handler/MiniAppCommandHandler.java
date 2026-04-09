package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.miniapp.config.MiniAppUrlResolver;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Handler untuk perintah {@code /app} — membuka Telegram Mini App via tombol WebApp.
 *
 * <p>Mengirimkan pesan dengan tombol inline yang langsung membuka Mini App di dalam
 * Telegram tanpa perlu keluar dari aplikasi. URL Mini App diambil dari {@link MiniAppUrlResolver}
 * yang di-resolve otomatis saat startup.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniAppCommandHandler extends AbstractCommandHandler {

    private final MiniAppUrlResolver urlResolver;

    @Override
    public String getCommand() {
        return "/app";
    }

    @Override
    public String getDescription() {
        return "Buka aplikasi Mini App untuk mencari tagihan, pelunasan, dan tabungan.";
    }

    @Override
    @RequireAuth
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String url = urlResolver.getUrl();

        if (url == null || url.isBlank()) {
            return Mono.fromRunnable(() ->
                sendMessage(chatId, "Mini App belum dikonfigurasi. Hubungi administrator.", client));
        }

        TdApi.InlineKeyboardButtonTypeWebApp webAppType = new TdApi.InlineKeyboardButtonTypeWebApp();
        webAppType.url = url;

        TdApi.InlineKeyboardButton button = new TdApi.InlineKeyboardButton();
        button.text = "📱 Buka Aplikasi";
        button.type = webAppType;

        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = new TdApi.InlineKeyboardButton[][]{{button}};

        return Mono.fromRunnable(() ->
            sendMessage(chatId, "Klik tombol di bawah untuk membuka aplikasi pencarian tagihan, pelunasan, dan tabungan:", keyboard, client));
    }
}
