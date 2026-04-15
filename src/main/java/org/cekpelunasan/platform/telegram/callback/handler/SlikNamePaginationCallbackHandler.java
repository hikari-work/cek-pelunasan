package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.service.slik.SlikNameFormatter;
import org.cekpelunasan.core.service.slik.SlikSessionCache;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.utils.button.SlikNamePaginationButton;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Handler untuk navigasi antar halaman hasil pencarian SLIK berdasarkan nama.
 *
 * <p>Callback berawalan {@code "slikn"} ini menangani perpindahan halaman pada
 * hasil pencarian SLIK (Sistem Layanan Informasi Keuangan) yang sebelumnya
 * sudah disimpan dalam {@link SlikSessionCache}. Sesi ini bersifat sementara —
 * jika sudah kedaluwarsa, user diminta mengulang pencarian.
 *
 * <p>Format data callback yang diharapkan: {@code "slikn_<nomor_halaman>"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlikNamePaginationCallbackHandler extends AbstractCallbackHandler {

    private final SlikSessionCache sessionCache;
    private final SlikNameFormatter formatter;
    private final SlikNamePaginationButton paginationButton;

    /**
     * Mengembalikan prefix {@code "slikn"} sebagai pengenal handler ini.
     */
    @Override
    public String getCallBackData() {
        return "slikn";
    }

    /**
     * Menampilkan halaman hasil SLIK yang diminta dari cache sesi pengguna.
     *
     * <p>Alur prosesnya: parse nomor halaman dari data callback → ambil sesi
     * yang tersimpan di cache berdasarkan chatId → validasi nomor halaman
     * masih dalam rentang yang valid → format halaman tersebut menjadi
     * {@link TdApi.FormattedText} → edit pesan dengan konten dan keyboard baru.
     *
     * <p>Jika sesi tidak ditemukan atau sudah habis, user mendapat pesan
     * instruksi untuk mengulang pencarian dengan perintah {@code /slik}.
     *
     * @param update event callback dari Telegram
     * @param client koneksi aktif ke Telegram
     * @return {@link Mono} yang selesai setelah pesan berhasil diedit
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return runBlocking(() -> {
            String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
            String[] parts = callbackData.split("_");

            int page;
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("Invalid page index in callback: {}", callbackData);
                return;
            }

            long chatId = update.chatId;
            SlikSessionCache.SlikSession session = sessionCache.get(chatId);

            if (session == null || session.pages().isEmpty()) {
                telegramMessageService.sendText(chatId, "⚠️ Sesi pencarian sudah habis, ulangi `/slik <nama>`", client);
                return;
            }

            List<SlikSessionCache.SlikPageData> pages = session.pages();
            if (page < 0 || page >= pages.size()) {
                log.warn("Page {} out of range ({}) for chat {}", page, pages.size(), chatId);
                return;
            }

            TdApi.FormattedText message = formatter.format(pages.get(page), page, pages.size());
            TdApi.ReplyMarkupInlineKeyboard keyboard = paginationButton.build(page, pages.size());
            telegramMessageService.editMessageWithFormattedMarkup(chatId, update.messageId, message, keyboard, client);

            log.info("SLIK name pagination — chat: {}, page: {}/{}", chatId, page + 1, pages.size());
        });
    }
}
