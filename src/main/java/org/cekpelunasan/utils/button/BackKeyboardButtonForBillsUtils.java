package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;

import java.nio.charset.StandardCharsets;

public class BackKeyboardButtonForBillsUtils {

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
