package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pusat pengiriman perintah — menerima semua pesan masuk dan meneruskan ke handler yang tepat.
 *
 * <p>Saat aplikasi pertama kali dijalankan, class ini mengumpulkan semua implementasi
 * {@link CommandProcessor} yang ada (misalnya handler untuk {@code /tagih}, {@code /slik}, dll)
 * dan menyimpannya dalam sebuah Map yang bisa di-lookup dengan cepat berdasarkan teks perintah.</p>
 *
 * <p>Ketika ada pesan masuk, class ini mengambil kata pertama dari teks pesan sebagai kunci perintah,
 * mencari handler yang sesuai, lalu menjalankannya secara asynchronous. Kalau tidak ada handler
 * yang cocok, pesan diteruskan ke handler fallback {@code /id} yang menangani input tidak dikenal.</p>
 */
@Component
public class CommandHandler {

    private final Map<String, CommandProcessor> processorMap;
    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    /**
     * Membangun peta perintah dari semua {@link CommandProcessor} yang terdaftar di Spring context.
     *
     * <p>Spring secara otomatis menginjeksikan semua bean yang mengimplementasikan
     * {@link CommandProcessor}, lalu class ini memetakan setiap perintah ke handler-nya masing-masing.
     * Kalau ada dua handler dengan perintah yang sama, salah satunya akan menimpa yang lain —
     * pastikan setiap handler punya perintah yang unik.</p>
     *
     * @param processorList daftar semua handler perintah yang ditemukan Spring di classpath
     */
    public CommandHandler(List<CommandProcessor> processorList) {
        this.processorMap = processorList.stream()
            .collect(Collectors.toMap(CommandProcessor::getCommand, p -> p));
    }

    /**
     * Menerima pesan masuk dan mendelegasikannya ke handler perintah yang sesuai.
     *
     * <p>Method ini berjalan secara asynchronous (ditandai dengan {@code @Async}) sehingga
     * tidak akan memblokir thread utama penerima update dari Telegram. Alur kerjanya:</p>
     * <ol>
     *   <li>Pesan yang bukan teks langsung diabaikan.</li>
     *   <li>Kata pertama dari teks diambil sebagai nama perintah (misalnya {@code /tagih}).</li>
     *   <li>Dicari handler yang cocok di map. Jika tidak ada, digunakan handler {@code /id} sebagai fallback.</li>
     *   <li>Handler yang ditemukan dijalankan dengan memanggil {@code process().subscribe()}.</li>
     * </ol>
     *
     * @param update objek update berisi detail pesan yang masuk dari Telegram
     * @param client koneksi aktif ke Telegram yang dipakai handler untuk mengirim balasan
     */
    @Async
    public void handle(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        try {
            if (!(update.message.content instanceof TdApi.MessageText messageText)) {
                return;
            }
            String text = messageText.text.text;
            String command = text.split(" ")[0];

            CommandProcessor processor = processorMap.get(command);
            if (processor == null) {
                processor = processorMap.get("/id");
            }
            if (processor != null) {
                processor.process(update, client).subscribe();
            }
        } catch (Exception e) {
            log.error("ERROR in handle: ", e);
        }
    }
}
