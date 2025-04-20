package org.cekpelunasan.utils.button;

import org.cekpelunasan.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class ButtonListForBills {

    public InlineKeyboardMarkup dynamicButtonName(Page<Bills> names, int currentPage, String query, String branch) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardRow paginationRow = new InlineKeyboardRow();

        int totalElements = (int) names.getTotalElements();
        int currentElement = currentPage * names.getSize() + 1;
        int maxElement = currentPage * names.getSize() + names.getNumberOfElements();

        if (names.hasPrevious()) {
            InlineKeyboardButton prev = InlineKeyboardButton.builder()
                    .text("⬅ Prev")
                    .callbackData("paging_" + query + "_" +  branch + "_" + (currentPage - 1))
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
                    .callbackData("paging_" + query + "_" +  branch + "_" + (currentPage + 1))
                    .build();
            paginationRow.add(next);
        }

        // Tambahkan pagination baris pertama
        rows.add(paginationRow);

        // ========== 🔘 DATA BUTTONS (2 per row) ==========

        InlineKeyboardRow currentRow = new InlineKeyboardRow();
        List<Bills> dataList = names.getContent();

        for (int i = 0; i < dataList.size(); i++) {
            Bills name = dataList.get(i);

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(name.getName())
                    .callbackData("tagihan_" + name.getNoSpk() + "_" + query + "_" + branch + "_" +currentPage)
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
