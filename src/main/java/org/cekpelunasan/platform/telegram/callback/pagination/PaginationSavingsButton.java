package org.cekpelunasan.platform.telegram.callback.pagination;

import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
public class PaginationSavingsButton {

	public InlineKeyboardMarkup keyboardMarkup(Page<Savings> page, String branch, int currentPage, String query) {
		return PaginationMarkupBuilder.buildWithBranch(page, currentPage, "tab", query, branch);
	}
}
