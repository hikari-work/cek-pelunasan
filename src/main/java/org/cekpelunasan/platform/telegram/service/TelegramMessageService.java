package org.cekpelunasan.platform.telegram.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Mengelola semua operasi pengiriman dan pengeditan pesan di Telegram via TDLight.
 * <p>
 * Satu class ini menjadi pusat untuk semua kebutuhan komunikasi ke Telegram:
 * kirim teks biasa, kirim teks dengan tombol inline keyboard, edit pesan yang ada,
 * hapus pesan, kirim dokumen/file, hingga parsing teks markdown agar tampil rapi di Telegram.
 * </p>
 * <p>
 * Semua method menggunakan {@link SimpleTelegramClient} dari TDLight (bukan Bot API biasa),
 * sehingga bisa beroperasi sebagai akun Telegram sungguhan, bukan sekadar bot.
 * Setiap operasi dibungkus try-catch dan timeout 5–10 detik agar tidak menggantung
 * kalau Telegram lambat merespons.
 * </p>
 * <p>
 * File yang dikirim lewat {@link #sendDocument} disimpan sementara di disk dan dihapus
 * otomatis 5 menit setelah pengiriman supaya tidak menumpuk di server.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageService {

    private final MessageIdResolver messageIdResolver;

    private static final ScheduledExecutorService FILE_CLEANUP =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tdlight-file-cleanup");
            t.setDaemon(true);
            return t;
        });

    /**
     * Mengirim pesan teks ke sebuah chat Telegram.
     * Teks diparse sebagai Markdown supaya format bold, italic, dan kode tampil dengan benar.
     *
     * @param chatId ID chat tujuan
     * @param text   isi pesan, mendukung format Markdown
     * @param client instance TDLight client yang aktif
     * @return ID pesan yang baru terkirim, atau 0 kalau gagal
     */
    public long sendText(long chatId, String text, SimpleTelegramClient client) {
        try {
            CompletableFuture<Long> future = new CompletableFuture<>();
            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            msg.inputMessageContent = content;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send text to {}: {}", chatId, result.getError().message);
                    future.complete(0L);
                } else {
                    future.complete(result.get().id);
                }
            });
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to send text to {}", chatId, e);
            return 0L;
        }
    }

    /**
     * Mengirim pesan teks beserta tombol-tombol inline keyboard di bawahnya.
     * Cocok untuk menampilkan menu pilihan atau navigasi halaman kepada pengguna.
     *
     * @param chatId   ID chat tujuan
     * @param text     isi pesan dengan dukungan Markdown
     * @param keyboard konfigurasi tombol inline keyboard yang akan ditampilkan
     * @param client   instance TDLight client yang aktif
     * @return ID pesan yang baru terkirim, atau 0 kalau gagal
     */
    public long sendTextWithKeyboard(long chatId, String text, TdApi.ReplyMarkupInlineKeyboard keyboard, SimpleTelegramClient client) {
        try {
            CompletableFuture<Long> future = new CompletableFuture<>();
            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            msg.replyMarkup = keyboard;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            msg.inputMessageContent = content;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send keyboard to {}: {}", chatId, result.getError().message);
                    future.complete(0L);
                } else {
                    future.complete(result.get().id);
                }
            });
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to send keyboard to {}", chatId, e);
            return 0L;
        }
    }

    /**
     * Mengedit isi teks dari pesan yang sudah terkirim sebelumnya.
     * Berguna untuk memperbarui tampilan tanpa mengirim pesan baru (menghindari spam).
     *
     * @param chatId    ID chat tempat pesan berada
     * @param messageId ID pesan yang akan diedit
     * @param text      isi baru pesan dengan dukungan Markdown
     * @param client    instance TDLight client yang aktif
     */
    public void editText(long chatId, long messageId, String text, SimpleTelegramClient client) {
        try {
            TdApi.EditMessageText edit = new TdApi.EditMessageText();
            edit.chatId = chatId;
            edit.messageId = messageId;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            edit.inputMessageContent = content;
            client.send(edit, result -> {
                if (result.isError()) {
                    log.error("Failed to edit message {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }

    /**
     * Mengedit pesan sekaligus memperbarui tombol inline keyboard-nya.
     * Dipakai saat navigasi halaman — teks dan tombol prev/next diperbarui bersamaan.
     *
     * @param chatId    ID chat tempat pesan berada
     * @param messageId ID pesan yang akan diedit
     * @param text      isi baru pesan
     * @param markup    konfigurasi tombol keyboard yang baru
     * @param client    instance TDLight client yang aktif
     */
    public void editMessageWithMarkup(long chatId, long messageId, String text, TdApi.ReplyMarkupInlineKeyboard markup, SimpleTelegramClient client) {
        try {
            TdApi.EditMessageText edit = new TdApi.EditMessageText();
            edit.chatId = chatId;
            edit.messageId = messageId;
            edit.replyMarkup = markup;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            edit.inputMessageContent = content;
            client.send(edit, result -> {
                if (result.isError()) {
                    log.error("Failed to edit message with markup {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to edit message with markup {} in chat {}", messageId, chatId, e);
        }
    }

    /**
     * Menghapus pesan dari chat Telegram untuk semua orang (revoke).
     *
     * @param chatId    ID chat tempat pesan berada
     * @param messageId ID pesan yang akan dihapus
     * @param client    instance TDLight client yang aktif
     */
    public void delete(long chatId, long messageId, SimpleTelegramClient client) {
        try {
            TdApi.DeleteMessages del = new TdApi.DeleteMessages();
            del.chatId = chatId;
            del.messageIds = new long[]{messageId};
            del.revoke = true;
            client.send(del, result -> {
                if (result.isError()) {
                    log.error("Failed to delete message {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to delete message {} in chat {}", messageId, chatId, e);
        }
    }

    /**
     * Mengirim file/dokumen ke chat Telegram.
     * <p>
     * File ditulis dulu ke disk sebagai file sementara, dikirim, lalu dihapus otomatis
     * 5 menit setelah pengiriman. Jeda 5 menit ini diperlukan karena TDLib mungkin
     * melakukan retry upload setelah callback pertama dipanggil.
     * </p>
     *
     * @param chatId   ID chat tujuan
     * @param fileName nama file yang akan ditampilkan di Telegram
     * @param bytes    konten file dalam bentuk array byte
     * @param client   instance TDLight client yang aktif
     */
    public void sendDocument(long chatId, String fileName, byte[] bytes, SimpleTelegramClient client) {
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("tdlight_", "_" + fileName);
            Files.write(tmpFile, bytes);

            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal();
            inputFile.path = tmpFile.toAbsolutePath().toString();

            TdApi.InputMessageDocument doc = new TdApi.InputMessageDocument();
            doc.document = inputFile;
            doc.caption = new TdApi.FormattedText("", new TdApi.TextEntity[0]);

            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            msg.inputMessageContent = doc;

            final Path finalTmpFile = tmpFile;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send document to {}: {}", chatId, result.getError().message);
                }
                // Delay deletion — TDLib may retry upload after callback fires
                FILE_CLEANUP.schedule(() -> {
                    try { Files.deleteIfExists(finalTmpFile); } catch (Exception ignored) {}
                }, 5, TimeUnit.MINUTES);
            });
        } catch (Exception e) {
            log.error("Failed to send document to {}", chatId, e);
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Alias dari {@link #sendTextWithKeyboard} dengan urutan parameter yang berbeda.
     * Tersedia untuk kompatibilitas kode yang sudah ada.
     *
     * @param chatId   ID chat tujuan
     * @param keyboard konfigurasi tombol inline keyboard
     * @param client   instance TDLight client yang aktif
     * @param text     isi pesan
     * @return ID pesan yang terkirim, atau 0 kalau gagal
     */
    public long sendKeyboard(long chatId, TdApi.ReplyMarkupInlineKeyboard keyboard, SimpleTelegramClient client, String text) {
        return sendTextWithKeyboard(chatId, text, keyboard, client);
    }

    /**
     * Mengirim pesan dengan tombol keyboard menggunakan {@link TdApi.FormattedText} yang sudah siap.
     * Dipakai kalau teks sudah diparse Markdown sebelumnya dan tidak perlu diparse ulang.
     *
     * @param chatId        ID chat tujuan
     * @param keyboard      konfigurasi tombol inline keyboard
     * @param client        instance TDLight client yang aktif
     * @param formattedText teks yang sudah diformat dengan entity Markdown
     * @return ID pesan yang terkirim, atau 0 kalau gagal
     */
    public long sendKeyboardFormatted(long chatId, TdApi.ReplyMarkupInlineKeyboard keyboard, SimpleTelegramClient client, TdApi.FormattedText formattedText) {
        try {
            CompletableFuture<Long> future = new CompletableFuture<>();
            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            msg.replyMarkup = keyboard;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = formattedText;
            msg.inputMessageContent = content;
            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send formatted keyboard to {}: {}", chatId, result.getError().message);
                    future.complete(0L);
                } else {
                    future.complete(result.get().id);
                }
            });
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to send formatted keyboard to {}", chatId, e);
            return 0L;
        }
    }

    /**
     * Mengedit pesan dengan teks terformat dan keyboard baru secara bersamaan.
     * Versi dari {@link #editMessageWithMarkup} yang menerima {@link TdApi.FormattedText}
     * langsung tanpa perlu parsing Markdown lagi.
     *
     * @param chatId        ID chat tempat pesan berada
     * @param messageId     ID pesan yang akan diedit
     * @param formattedText teks baru yang sudah diformat
     * @param markup        keyboard baru yang akan menggantikan keyboard lama
     * @param client        instance TDLight client yang aktif
     */
    public void editMessageWithFormattedMarkup(long chatId, long messageId, TdApi.FormattedText formattedText, TdApi.ReplyMarkupInlineKeyboard markup, SimpleTelegramClient client) {
        try {
            TdApi.EditMessageText edit = new TdApi.EditMessageText();
            edit.chatId = chatId;
            edit.messageId = messageId;
            edit.replyMarkup = markup;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = formattedText;
            edit.inputMessageContent = content;
            client.send(edit, result -> {
                if (result.isError()) {
                    log.error("Failed to edit formatted message {} in chat {}: {}", messageId, chatId, result.getError().message);
                }
            });
        } catch (Exception e) {
            log.error("Failed to edit formatted message {} in chat {}", messageId, chatId, e);
        }
    }

    /**
     * Mengirim pesan teks dan menunggu hingga server Telegram mengonfirmasi ID final-nya.
     * <p>
     * TDLib mengembalikan <em>local temporary ID</em> dari callback {@code SendMessage}
     * (contoh: 1048577). ID asli yang bisa dipakai untuk {@link #editText} baru tersedia
     * setelah event {@code UpdateMessageSendSucceeded} diterima — event ini ditangkap oleh
     * {@link org.cekpelunasan.platform.telegram.bot.TelegramBot} dan diselesaikan lewat
     * {@link MessageIdResolver#resolve}.
     * </p>
     * <p>
     * Jika server tidak merespons dalam 10 detik, method ini fallback ke local ID dan
     * mencatat peringatan. Edit pesan berikutnya kemungkinan gagal jika local ID berbeda
     * dari server ID, tapi ini hanya terjadi jika koneksi sangat lambat.
     * </p>
     *
     * @param chatId ID chat tujuan
     * @param text   isi pesan dengan dukungan Markdown
     * @param client instance TDLight client yang aktif
     * @return server message ID yang valid, atau local ID sebagai fallback
     */
    public long sendTextVerified(long chatId, String text, SimpleTelegramClient client) {
        try {
            CompletableFuture<Long> localIdFuture = new CompletableFuture<>();

            TdApi.SendMessage msg = new TdApi.SendMessage();
            msg.chatId = chatId;
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = parseMarkdown(text, client);
            msg.inputMessageContent = content;

            client.send(msg, result -> {
                if (result.isError()) {
                    log.error("Failed to send text to {}: {}", chatId, result.getError().message);
                    localIdFuture.complete(0L);
                } else {
                    long localId = result.get().id;
                    // Daftarkan SEBELUM menyelesaikan localIdFuture agar tidak ada race condition
                    messageIdResolver.register(localId);
                    localIdFuture.complete(localId);
                }
            });

            long localId = localIdFuture.get(10, TimeUnit.SECONDS);
            if (localId <= 0L) return 0L;

            // Tunggu UpdateMessageSendSucceeded menyediakan server ID
            try {
                CompletableFuture<Long> serverIdFuture = messageIdResolver.getFuture(localId);
                return serverIdFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Timeout menunggu server ID untuk local message {} di chat {}, fallback ke local ID",
                    localId, chatId);
                messageIdResolver.cancel(localId);
                return localId;
            }
        } catch (Exception e) {
            log.error("Failed to send verified text to {}", chatId, e);
            return 0L;
        }
    }

    /**
     * Mengurai teks markdown menjadi {@link TdApi.FormattedText} yang dimengerti Telegram.
     * <p>
     * Telegram menggunakan format entity internal (bukan raw Markdown) untuk menampilkan
     * teks yang diformat. Method ini menggunakan TDLib sendiri untuk melakukan konversi
     * supaya hasilnya sesuai standar Telegram. Kalau parsing gagal atau timeout,
     * teks dikembalikan tanpa formatting apapun.
     * </p>
     *
     * @param text   teks dengan format Markdown yang akan diurai
     * @param client instance TDLight client yang aktif untuk menjalankan parsing
     * @return {@link TdApi.FormattedText} siap pakai, atau teks biasa tanpa entity kalau gagal
     */
    public TdApi.FormattedText parseMarkdown(String text, SimpleTelegramClient client) {
        try {
            CompletableFuture<TdApi.FormattedText> future = new CompletableFuture<>();
            TdApi.ParseTextEntities req = new TdApi.ParseTextEntities();
            req.text = text;
            req.parseMode = new TdApi.TextParseModeMarkdown(1);
            client.send(req, result -> {
                if (result.isError()) {
                    future.complete(new TdApi.FormattedText(text, new TdApi.TextEntity[0]));
                } else {
                    future.complete(result.get());
                }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new TdApi.FormattedText(text, new TdApi.TextEntity[0]);
        }
    }
}
