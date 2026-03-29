package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class PaginationToMinimalPay {

    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<Bills> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "minimal", query);
    }
}
