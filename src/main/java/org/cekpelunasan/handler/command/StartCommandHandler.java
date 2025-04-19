package org.cekpelunasan.handler.command;

import org.cekpelunasan.service.AuthorizedChats;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
public class StartCommandHandler implements CommandProcessor {

    private final AuthorizedChats authService;
    private final MessageTemplate messageTemplateService;

    private static final String START_MESSAGE = """
            👋 *Halo! Selamat datang di Bot Pelunasan.*

            Bot ini bukan tempat tanya jodoh, ya. Saya cuma bisa bantu cek *pelunasan*

            Berikut beberapa perintah yang bisa kamu pakai:

            🔹 */pl <No SPK>* — Cek pelunasan nasabah
            🔹 */fi <Nama>* — Cari nasabah by nama
            🔹 */help* — Kalau kamu butuh bimbingan hidup (atau cuma mau lihat perintah)

            📌 *Kalau kamu belum terdaftar*, jangan baper. Ketik `.id`, kirim ke admin, dan sabar tunggu restu. 🧘‍♂️

            📌 Kalau mau curhat bisa langsung ke admin ya, kirim aja disini, siapa tahu mau ramalan zodiak kamu

            Yuk, langsung aja dicoba.
            """;

    public StartCommandHandler(AuthorizedChats authService, MessageTemplate messageTemplateService) {
        this.authService = authService;
        this.messageTemplateService = messageTemplateService;
    }

    @Override
    public String getCommand() {
        return "/start";
    }


    @Override
    @Async
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            Long chatId = update.getMessage().getChatId();
            if (authService.isAuthorized(chatId)) {
                sendMessage(chatId, START_MESSAGE, telegramClient);
            } else {
                sendMessage(chatId, messageTemplateService.unathorizedMessage(), telegramClient);
            }
        });
    }
}
