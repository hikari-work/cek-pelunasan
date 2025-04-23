package org.cekpelunasan.utils.button;

import org.cekpelunasan.entity.Repayment;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class ButtonListForName {

    public InlineKeyboardMarkup dynamicButtonName(Page<Repayment> page, int currentPage, String query) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(buildPaginationRow(page, currentPage, query));

        rows.addAll(buildDataRows(page.getContent(), currentPage, query));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardRow buildPaginationRow(Page<?> page, int currentPage, String query) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        int from = currentPage * page.getSize() + 1;
        int to = from + page.getNumberOfElements() - 1;
        int total = (int) page.getTotalElements();

        if (page.hasPrevious()) {
            row.add(buildButton("⬅ Prev", "page_" + query + "_" + (currentPage - 1)));
        }

        row.add(buildButton(from + " - " + to + " / " + total, "noop"));

        if (page.hasNext()) {
            row.add(buildButton("Next ➡", "page_" + query + "_" + (currentPage + 1)));
        }

        return row;
    }

    private List<InlineKeyboardRow> buildDataRows(List<Repayment> data, int currentPage, String query) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow currentRow = new InlineKeyboardRow();

        for (int i = 0; i < data.size(); i++) {
            Repayment r = data.get(i);
            currentRow.add(buildButton(
                    r.getName(),
                    "pelunasan_" + r.getCustomerId() + "_" + query + "_" + currentPage
            ));

            if (currentRow.size() == 2 || i == data.size() - 1) {
                rows.add(currentRow);
                currentRow = new InlineKeyboardRow();
            }
        }
        return rows;
    }

    private InlineKeyboardButton buildButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
}
