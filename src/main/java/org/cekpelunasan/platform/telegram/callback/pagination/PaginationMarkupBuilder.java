package org.cekpelunasan.platform.telegram.callback.pagination;

import org.springframework.data.domain.Page;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

public class PaginationMarkupBuilder {

	/**
	 * Builds pagination markup with callback format: {prefix}_{query}_{page}
	 */
	public static InlineKeyboardMarkup build(Page<?> page, int currentPage, String callbackPrefix, String query) {
		InlineKeyboardRow row = new InlineKeyboardRow();
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

		return InlineKeyboardMarkup.builder().keyboard(List.of(row)).build();
	}

	/**
	 * Builds pagination markup with callback format: {prefix}_{query}_{branch}_{page}
	 */
	public static InlineKeyboardMarkup buildWithBranch(Page<?> page, int currentPage, String callbackPrefix, String query, String branch) {
		InlineKeyboardRow row = new InlineKeyboardRow();
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

		return InlineKeyboardMarkup.builder().keyboard(List.of(row)).build();
	}

	private static InlineKeyboardButton button(String text, String callbackData) {
		return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
	}
}
