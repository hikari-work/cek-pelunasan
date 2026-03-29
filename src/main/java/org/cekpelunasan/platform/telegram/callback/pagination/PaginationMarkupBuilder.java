package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.data.domain.Page;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PaginationMarkupBuilder {

    public static TdApi.ReplyMarkupInlineKeyboard build(Page<?> page, int currentPage, String callbackPrefix, String query) {
        List<TdApi.InlineKeyboardButton> row = new ArrayList<>();
        int total = (int) page.getTotalElements();
        int from = currentPage * page.getSize() + 1;
        int to = from + page.getNumberOfElements() - 1;

        if (page.hasPrevious()) {
            row.add(button("⬅ Prev", callbackPrefix + "_" + query + "_" + (currentPage - 1)));
        }
        row.add(button(from + " - " + to + " / " + total, "noop"));
        if (page.hasNext()) {
            row.add(button("Next ➡", callbackPrefix + "_" + query + "_" + (currentPage + 1)));
        }

        TdApi.InlineKeyboardButton[][] rows = {row.toArray(new TdApi.InlineKeyboardButton[0])};
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    public static TdApi.ReplyMarkupInlineKeyboard buildWithBranch(Page<?> page, int currentPage, String callbackPrefix, String query, String branch) {
        List<TdApi.InlineKeyboardButton> row = new ArrayList<>();
        int total = (int) page.getTotalElements();
        int from = currentPage * page.getSize() + 1;
        int to = from + page.getNumberOfElements() - 1;

        if (page.hasPrevious()) {
            row.add(button("⬅ Prev", callbackPrefix + "_" + query + "_" + branch + "_" + (currentPage - 1)));
        }
        row.add(button(from + " - " + to + " / " + total, "noop"));
        if (page.hasNext()) {
            row.add(button("Next ➡", callbackPrefix + "_" + query + "_" + branch + "_" + (currentPage + 1)));
        }

        TdApi.InlineKeyboardButton[][] rows = {row.toArray(new TdApi.InlineKeyboardButton[0])};
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    static TdApi.InlineKeyboardButton button(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
