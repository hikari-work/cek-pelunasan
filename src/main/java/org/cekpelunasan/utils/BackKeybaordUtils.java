package org.cekpelunasan.utils;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class BackKeybaordUtils {

    public InlineKeyboardMarkup backButton(String query) {
        int page = Integer.parseInt(query.split("_")[3]);
        String queryName = query.split("_")[2];
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text("🔙 Kembali")
                .callbackData("page_" + queryName + "_" + page)
                .build();
        inlineKeyboardButtons.add(backButton);
        rows.add(inlineKeyboardButtons);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}
