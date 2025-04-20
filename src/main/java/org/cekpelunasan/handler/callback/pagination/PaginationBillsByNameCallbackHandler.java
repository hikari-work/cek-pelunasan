package org.cekpelunasan.handler.callback.pagination;

import org.cekpelunasan.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class PaginationBillsByNameCallbackHandler {

    public InlineKeyboardMarkup dynamicButtonName(Page<Bills> names, int currentPage, String query) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardRow paginationRow = new InlineKeyboardRow();

        int totalElements = (int) names.getTotalElements();
        int currentElement = currentPage * names.getSize() + 1;
        int maxElement = currentPage * names.getSize() + names.getNumberOfElements();

        if (names.hasPrevious()) {
            InlineKeyboardButton prev = InlineKeyboardButton.builder()
                    .text("⬅ Prev")
                    .callbackData("pagebills_" + query + "_" + (currentPage - 1))
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
                    .callbackData("pagebills_" + query + "_" + (currentPage + 1))
                    .build();
            paginationRow.add(next);
        }

        // Tambahkan pagination baris pertama
        rows.add(paginationRow);


        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }
}
