package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;
import org.springframework.data.domain.Page;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class ButtonListForName {

    public InlineKeyboardMarkup dynamicButtonName(Page<Repayment> names, int currentPage, String query) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        // ========== ⬆️ PAGINATION ROW ==========

        InlineKeyboardRow paginationRow = new InlineKeyboardRow();

        int totalElements = (int) names.getTotalElements();
        int currentElement = currentPage * names.getSize() + 1;
        int maxElement = currentPage * names.getSize() + names.getNumberOfElements();

        if (names.hasPrevious()) {
            InlineKeyboardButton prev = InlineKeyboardButton.builder()
                    .text("⬅ Prev")
                    .callbackData("page_" + query + "_" + (currentPage - 1))
                    .build();
            paginationRow.add(prev);
        }

        InlineKeyboardButton middle = InlineKeyboardButton.builder()
                .text(currentElement + " - " + maxElement + " / " + totalElements)
                .callbackData("noop")
                .build();
        paginationRow.add(middle);

        if (names.hasNext()) {
            InlineKeyboardButton next = InlineKeyboardButton.builder()
                    .text("Next ➡")
                    .callbackData("page_" + query + "_" + (currentPage + 1))
                    .build();
            paginationRow.add(next);
        }

        // Tambahkan pagination baris pertama
        if (!paginationRow.isEmpty()) {
            rows.add(paginationRow);
        }

        // ========== 🔘 DATA BUTTONS (2 per row) ==========

        InlineKeyboardRow currentRow = new InlineKeyboardRow();
        List<Repayment> dataList = names.getContent();

        for (int i = 0; i < dataList.size(); i++) {
            Repayment name = dataList.get(i);

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(name.getName())
                    .callbackData("pelunasan_" + name.getCustomerId() + "_" + query + "_" + currentPage)
                    .build();

            currentRow.add(button);

            if (currentRow.size() == 2 || i == dataList.size() - 1) {
                rows.add(currentRow);
                currentRow = new InlineKeyboardRow();
            }
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}
