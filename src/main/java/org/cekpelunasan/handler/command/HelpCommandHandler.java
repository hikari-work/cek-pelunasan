package org.cekpelunasan.handler.command;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class HelpCommandHandler implements CommandProcessor {

    private static final String HELP_MESSAGE = """
        🆘 *Panduan Penggunaan Bot Pelunasan* 🆘

        Berikut ini adalah daftar perintah yang dapat kamu gunakan:

        🔹 */pl [nomor]* — Cari nasabah berdasarkan nomor SPK.
        Contoh: `/pl 117204000345`

        🔹 */fi [nama]* — Cari nasabah berdasarkan Nama.
        Contoh: `/fi Budi`

        🔹 */next* dan */prev* — Navigasi halaman hasil pencarian.
        Gunakan setelah pencarian untuk pindah halaman.

        🔹 */status* — Tampilkan status bot, termasuk load sistem dan koneksi database.

        🔹 */help* — Tampilkan pesan bantuan ini.

        ℹ️ *Catatan*: Gunakan kata kunci yang spesifik untuk hasil pencarian terbaik.
        
        🔐 Data yang ditampilkan bersifat pribadi. Gunakan dengan bijak.

        🙏 Terima kasih telah menggunakan Pelunasan Bot!
        """;

    @Override
    public String getCommand() {
        return "/help";
    }

    @Override
    public void process(Update update, TelegramClient telegramClient) {
        if (isHelpCommand(update)) {
            sendHelpMessage(update.getMessage().getChatId(), telegramClient);
        }
    }

    private boolean isHelpCommand(Update update) {
        String messageText = update.getMessage().getText();
        return messageText != null && messageText.trim().startsWith(getCommand());
    }

    private void sendHelpMessage(Long chatId, TelegramClient telegramClient) {
        sendMessage(chatId, HELP_MESSAGE, telegramClient);
    }
}
