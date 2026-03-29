package org.cekpelunasan.platform.telegram.callback.pagination;

import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
public class PaginationBillsByNameCallbackHandler {

	public InlineKeyboardMarkup dynamicButtonName(Page<Bills> page, int currentPage, String query) {
		return PaginationMarkupBuilder.build(page, currentPage, "pagebills", query);
	}
}
