package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.data.domain.Page;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilitas pembangun inline keyboard untuk paginasi pada semua fitur bot.
 *
 * <p>Class ini adalah pusat logika pembuatan tombol navigasi halaman. Semua
 * class paginasi di package ini (seperti {@link PaginationBillsByNameCallbackHandler},
 * {@link PaginationSavingsButton}, dst.) cukup memanggil method static di sini
 * tanpa perlu menduplikasi logika pembangunan tombol.
 *
 * <p>Ada dua varian method utama:
 * <ul>
 *   <li>{@link #build} — untuk query tanpa cabang (prefix + query + halaman)</li>
 *   <li>{@link #buildWithBranch} — untuk query yang juga membawa kode cabang</li>
 * </ul>
 */
public class PaginationMarkupBuilder {

    /**
     * Membangun inline keyboard paginasi standar tanpa parameter cabang.
     *
     * <p>Menghasilkan satu baris tombol yang terdiri dari:
     * <ul>
     *   <li>Tombol "⬅ Prev" (hanya jika ada halaman sebelumnya)</li>
     *   <li>Tombol informasi posisi, misalnya "1 - 5 / 20" (selalu ada, callback = "noop")</li>
     *   <li>Tombol "Next ➡" (hanya jika ada halaman berikutnya)</li>
     * </ul>
     *
     * @param page           halaman data yang sedang ditampilkan
     * @param currentPage    nomor halaman saat ini (0-based)
     * @param callbackPrefix prefix callback yang menentukan handler mana yang dipanggil
     * @param query          parameter pencarian yang disisipkan ke dalam data callback tombol
     * @return objek {@link TdApi.ReplyMarkupInlineKeyboard} satu baris berisi tombol navigasi
     */
    public static TdApi.ReplyMarkupInlineKeyboard build(Page<?> page, int currentPage, String callbackPrefix, String query) {
        List<TdApi.InlineKeyboardButton> row = new ArrayList<>();
        int total = (int) page.getTotalElements();
        int from = currentPage * page.getSize() + 1;
        int to = from + page.getNumberOfElements() - 1;

        if (page.hasPrevious()) {
            row.add(button("⬅ Prev", callbackPrefix + "_" + query + "_" + (currentPage - 1)));
        }
        row.add(button(from + " - " + to + " / " + total, "noop"));
        if (page.hasNext()) {
            row.add(button("Next ➡", callbackPrefix + "_" + query + "_" + (currentPage + 1)));
        }

        TdApi.InlineKeyboardButton[][] rows = {row.toArray(new TdApi.InlineKeyboardButton[0])};
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    /**
     * Membangun inline keyboard paginasi dengan parameter cabang tambahan.
     *
     * <p>Digunakan untuk fitur yang membutuhkan query + cabang sekaligus,
     * misalnya navigasi daftar tabungan yang sudah difilter berdasarkan nama
     * nasabah dan kode cabang tertentu. Data callback tombol mengikuti format:
     * {@code "<prefix>_<query>_<cabang>_<nomor_halaman>"}.
     *
     * @param page           halaman data yang sedang ditampilkan
     * @param currentPage    nomor halaman saat ini (0-based)
     * @param callbackPrefix prefix callback yang menentukan handler mana yang dipanggil
     * @param query          parameter pencarian (biasanya nama nasabah)
     * @param branch         kode cabang yang ikut disertakan dalam data callback
     * @return objek {@link TdApi.ReplyMarkupInlineKeyboard} satu baris berisi tombol navigasi
     */
    public static TdApi.ReplyMarkupInlineKeyboard buildWithBranch(Page<?> page, int currentPage, String callbackPrefix, String query, String branch) {
        List<TdApi.InlineKeyboardButton> row = new ArrayList<>();
        int total = (int) page.getTotalElements();
        int from = currentPage * page.getSize() + 1;
        int to = from + page.getNumberOfElements() - 1;

        if (page.hasPrevious()) {
            row.add(button("⬅ Prev", callbackPrefix + "_" + query + "_" + branch + "_" + (currentPage - 1)));
        }
        row.add(button(from + " - " + to + " / " + total, "noop"));
        if (page.hasNext()) {
            row.add(button("Next ➡", callbackPrefix + "_" + query + "_" + branch + "_" + (currentPage + 1)));
        }

        TdApi.InlineKeyboardButton[][] rows = {row.toArray(new TdApi.InlineKeyboardButton[0])};
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    /**
     * Membuat satu tombol inline keyboard dengan teks dan data callback yang diberikan.
     *
     * @param text teks yang ditampilkan pada tombol
     * @param data string yang dikirim ke bot ketika tombol ini ditekan
     * @return objek {@link TdApi.InlineKeyboardButton} yang siap dipakai
     */
    static TdApi.InlineKeyboardButton button(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
