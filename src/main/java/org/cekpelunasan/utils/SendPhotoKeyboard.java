package org.cekpelunasan.utils;


import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class SendPhotoKeyboard {

    public InlineKeyboardMarkup sendPhotoButton(Long query) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();

        InlineKeyboardButton photoButton = InlineKeyboardButton.builder()
                .text("📸 Kirim Gambar")
                .callbackData("photo_" + query)
                .build();
        inlineKeyboardButtons.add(photoButton);
        rows.add(inlineKeyboardButtons);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}
