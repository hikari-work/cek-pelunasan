package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PaginationCanvassingByTab {

    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<?> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "canvas", query);
    }
}
