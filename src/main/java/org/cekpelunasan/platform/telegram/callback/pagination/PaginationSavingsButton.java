package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PaginationSavingsButton {

    public TdApi.ReplyMarkupInlineKeyboard keyboardMarkup(Page<Savings> page, String branch, int currentPage, String query) {
        return PaginationMarkupBuilder.buildWithBranch(page, currentPage, "tab", query, branch);
    }
}
