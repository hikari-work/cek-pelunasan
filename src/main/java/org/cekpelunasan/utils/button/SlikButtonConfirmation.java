package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Membuat tombol konfirmasi untuk perintah cek data SLIK nasabah.
 * <p>
 * Setelah data SLIK nasabah ditemukan, pengguna ditanya mau dikirim data mana:
 * hanya fasilitas yang masih aktif, atau semua data termasuk yang sudah lunas.
 * Class ini menyusun dua tombol pilihan tersebut.
 * </p>
 */
@Component
public class SlikButtonConfirmation {

    /**
     * Membuat keyboard konfirmasi berisi dua pilihan pengiriman data SLIK.
     * Data callback format: "slik_[query]_1" untuk aktif saja, "slik_[query]_0" untuk semua data.
     *
     * @param query nama atau identifikasi nasabah yang data SLIK-nya akan dikirim
     * @return keyboard inline dengan dua tombol pilihan
     */
    public TdApi.ReplyMarkupInlineKeyboard sendSlikCommand(String query) {
        TdApi.InlineKeyboardButton aktifButton = tdButton("Kirim Data Fasilitas Aktif", "slik_" + query + "_1");
        TdApi.InlineKeyboardButton allButton = tdButton("Kirim Semua Data", "slik_" + query + "_0");
        TdApi.InlineKeyboardButton[][] rows = {{aktifButton, allButton}};
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
