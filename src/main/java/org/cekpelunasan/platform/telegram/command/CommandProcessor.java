package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import reactor.core.publisher.Mono;

/**
 * Kontrak dasar yang harus dipenuhi oleh setiap handler perintah bot.
 *
 * <p>Setiap command yang dikenali bot — misalnya {@code /tagih}, {@code /slik}, atau {@code /status} —
 * wajib mengimplementasikan interface ini. Dengan cara ini, {@link CommandHandler} bisa
 * memperlakukan semua perintah secara seragam tanpa perlu tahu detail masing-masing implementasinya.</p>
 *
 * <p>Terdapat dua varian method {@code process}: satu menerima objek update penuh dari Telegram,
 * dan satu lagi versi yang sudah disederhanakan dengan hanya menerima {@code chatId} dan teks pesan.
 * Implementasi biasanya menggunakan varian kedua yang lebih sederhana.</p>
 */
public interface CommandProcessor {

    /**
     * Mengembalikan string perintah yang ditangani oleh implementasi ini.
     *
     * <p>Nilai yang dikembalikan harus diawali dengan {@code /}, misalnya {@code "/tagih"} atau {@code "/slik"}.
     * String ini digunakan sebagai kunci lookup di {@link CommandHandler}.</p>
     *
     * @return string perintah, contoh: {@code "/tagih"}
     */
    String getCommand();

    /**
     * Mengembalikan deskripsi singkat tentang apa yang dilakukan perintah ini.
     *
     * <p>Teks ini bisa ditampilkan di menu bantuan atau dokumentasi bot.</p>
     *
     * @return deskripsi perintah dalam bahasa Indonesia
     */
    String getDescription();

    /**
     * Memproses pesan update mentah yang datang langsung dari Telegram.
     *
     * <p>Implementasi default di {@link AbstractCommandHandler} sudah mengekstrak
     * {@code chatId} dan teks pesan, lalu meneruskannya ke overload yang lebih sederhana.
     * Override method ini hanya jika kamu perlu mengakses data update di luar teks pesan,
     * misalnya untuk membaca {@code replyTo} atau {@code messageId}.</p>
     *
     * @param update objek update lengkap dari Telegram
     * @param client koneksi aktif ke Telegram untuk mengirim balasan
     * @return {@link Mono} yang merepresentasikan proses asynchronous eksekusi perintah
     */
    Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client);

    /**
     * Versi sederhana dari {@code process} yang sudah menerima {@code chatId} dan teks pesan.
     *
     * <p>Sebagian besar implementasi cukup meng-override method ini karena pada umumnya
     * hanya perlu dua hal: tahu siapa yang mengirim pesan dan apa isinya.
     * Secara default method ini mengembalikan {@link Mono#empty()} tanpa melakukan apa-apa.</p>
     *
     * @param chatId ID chat Telegram dari pengirim pesan
     * @param text   isi teks pesan yang dikirimkan user
     * @param client koneksi aktif ke Telegram untuk mengirim balasan
     * @return {@link Mono} yang merepresentasikan proses asynchronous eksekusi perintah
     */
    default Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        return Mono.empty();
    }
}
