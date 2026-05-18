package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Membuat tombol navigasi halaman (pagination) untuk daftar nama SLIK yang ditemukan.
 * <p>
 * Ketika pencarian SLIK menghasilkan lebih dari satu nama yang cocok, pengguna bisa
 * menavigasi hasil satu per satu menggunakan tombol Prev dan Next.
 * Tombol info di tengah menunjukkan posisi saat ini (misal "2 / 5").
 * </p>
 */
@Component
public class SlikNamePaginationButton {

    /**
     * Membuat keyboard navigasi untuk daftar hasil pencarian SLIK.
     * Tombol Prev hanya muncul kalau bukan halaman pertama, tombol Next hanya muncul kalau bukan terakhir.
     *
     * @param current indeks halaman saat ini (0-indexed)
     * @param total   total jumlah hasil yang ditemukan
     * @return keyboard inline dengan tombol navigasi yang sesuai
     */
    public TdApi.ReplyMarkupInlineKeyboard build(int current, int total) {
        List<TdApi.InlineKeyboardButton> row = new ArrayList<>();

        if (current > 0) {
            row.add(tdButton("◀ Prev", "slikn_" + (current - 1)));
        }

        row.add(tdButton(String.format("%d / %d", current + 1, total), "noop_slik"));

        if (current < total - 1) {
            row.add(tdButton("Next ▶", "slikn_" + (current + 1)));
        }

        TdApi.InlineKeyboardButton[][] rows = {row.toArray(new TdApi.InlineKeyboardButton[0])};
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    private TdApi.InlineKeyboardButton tdButton(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
