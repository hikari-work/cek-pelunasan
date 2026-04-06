package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SlikButtonConfirmation {

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
