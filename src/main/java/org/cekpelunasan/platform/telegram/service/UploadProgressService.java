package org.cekpelunasan.platform.telegram.service;

import it.tdlight.client.SimpleTelegramClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mengelola tampilan progress bar di Telegram saat proses upload CSV berlangsung.
 * <p>
 * Format pesan yang ditampilkan:
 * <pre>
 * ⬆️ Mengimpor Data Tagihan...
 *
 * 75.000 |||||..... 150.000
 * 50%
 * </pre>
 * </p>
 * <p>
 * Proses verifikasi messageId dilakukan lewat {@link TelegramMessageService#sendTextVerified}
 * agar edit pesan berikutnya selalu mengarah ke ID yang valid di server Telegram.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadProgressService {

    private static final int BAR_WIDTH = 12;

    private final TelegramMessageService telegramMessageService;

    /**
     * Menghitung jumlah baris data (tidak termasuk header) dalam sebuah file CSV
     * dengan cara membaca byte mentah — sangat cepat bahkan untuk file besar.
     *
     * @param path path file CSV yang akan dihitung
     * @return jumlah baris data (total baris dikurangi 1 untuk header), minimum 0
     */
    public long countLines(Path path) {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(path), 65536)) {
            byte[] buf = new byte[8192];
            long count = 0;
            int read;
            while ((read = is.read(buf)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buf[i] == '\n') count++;
                }
            }
            // Subtract 1 for the header line; guard against 0
            return Math.max(0, count - 1);
        } catch (IOException e) {
            log.warn("Gagal menghitung baris file {}: {}", path, e.getMessage());
            return 0;
        }
    }

    /**
     * Mengirim pesan progress awal ke Telegram (0%) dan mengembalikan messageId
     * yang sudah diverifikasi sehingga bisa diedit ulang dengan benar.
     *
     * @param chatId ID chat tujuan
     * @param label  nama data yang sedang diimpor, misalnya "Data Tagihan"
     * @param total  total baris data yang akan diproses
     * @param client koneksi aktif ke Telegram
     * @return messageId yang valid di server Telegram, atau 0 jika gagal
     */
    public long sendProgressMessage(long chatId, String label, long total, SimpleTelegramClient client) {
        String text = buildText(label, 0, total);
        return telegramMessageService.sendTextVerified(chatId, text, client);
    }

    /**
     * Mengedit pesan progress yang sudah ada dengan data terkini.
     * Jika messageId tidak valid (0), operasi ini diabaikan.
     *
     * @param chatId    ID chat tempat pesan berada
     * @param messageId ID pesan yang akan diedit (hasil dari {@link #sendProgressMessage})
     * @param label     nama data yang sedang diimpor
     * @param processed jumlah baris yang sudah berhasil diproses
     * @param total     total keseluruhan baris
     * @param client    koneksi aktif ke Telegram
     */
    public void updateProgress(long chatId, long messageId, String label,
                               long processed, long total, SimpleTelegramClient client) {
        if (messageId <= 0) return;
        String text = buildText(label, processed, total);
        telegramMessageService.editText(chatId, messageId, text, client);
    }

    /**
     * Membangun teks progress bar.
     * <p>
     * Contoh output (25%):
     * <pre>
     * Updating
     * [■■■□□□□□□□□□] 25%
     * Processed: 4321
     * Size: 17293
     * </pre>
     * </p>
     */
    private String buildText(String label, long processed, long total) {
        int percent = (total > 0) ? (int) Math.min(100, processed * 100 / total) : 0;
        int filled = percent * BAR_WIDTH / 100;
        int empty = BAR_WIDTH - filled;
        String bar = "[" + "■".repeat(filled) + "□".repeat(empty) + "]";
        return String.format(
            "Updating\n%s %d%%\nProcessed: %d\nSize: %d",
            bar, percent, processed, total
        );
    }
}
