package org.cekpelunasan.platform.telegram.callback.pagination;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
public class PaginationCanvassingButton {

	public InlineKeyboardMarkup dynamicButtonName(Page<?> page, int currentPage, String query) {
		return PaginationMarkupBuilder.build(page, currentPage, "canvasing", query);
	}
}
