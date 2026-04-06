package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;

import java.nio.charset.StandardCharsets;

/**
 * Membuat tombol "Kembali" untuk navigasi dari halaman detail tagihan ke halaman daftar.
 * <p>
 * Ketika pengguna membuka detail tagihan satu nasabah, tombol ini muncul di bawah pesan
 * supaya pengguna bisa kembali ke halaman daftar sebelumnya tanpa harus mencari ulang.
 * Informasi tentang halaman mana yang harus dikembalikan disimpan di dalam data callback tombol.
 * </p>
 */
public class BackKeyboardButtonForBillsUtils {

    /**
     * Membuat keyboard inline berisi tombol "Kembali" yang mengarahkan ke halaman daftar tagihan.
     * <p>
     * Format query yang diharapkan: "detail_[something]_[name]_[branch]_[page]".
     * Data callback yang disimpan di tombol menggunakan format "paging_[name]_[branch]_[page]".
     * </p>
     *
     * @param query string query callback dari tombol detail yang sebelumnya ditekan
     * @return keyboard inline dengan satu tombol "Kembali"
     */
    public TdApi.ReplyMarkupInlineKeyboard backButton(String query) {
        String[] parts = query.split("_");
        String name = parts[2];
        String branch = parts[3];
        String page = parts[4];

        TdApi.InlineKeyboardButton backButton = tdButton("◀️ Kembali", "paging_" + name + "_" + branch + "_" + page);
        TdApi.InlineKeyboardButton[][] rows = {{backButton}};
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    private static TdApi.InlineKeyboardButton tdButton(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
