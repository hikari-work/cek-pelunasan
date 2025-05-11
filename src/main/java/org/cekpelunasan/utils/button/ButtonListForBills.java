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

		rows.add(buildPaginationRow(names, currentPage, query, branch));
		rows.addAll(buildDataButtons(names.getContent(), currentPage, query, branch));

		return InlineKeyboardMarkup.builder()
			.keyboard(rows)
			.build();
	}

	private InlineKeyboardRow buildPaginationRow(Page<Bills> page, int currentPage, String query, String branch) {
		InlineKeyboardRow row = new InlineKeyboardRow();

		int totalElements = (int) page.getTotalElements();
		int currentElement = currentPage * page.getSize() + 1;
		int maxElement = currentPage * page.getSize() + page.getNumberOfElements();

		if (page.hasPrevious()) {
			row.add(InlineKeyboardButton.builder()
				.text("⬅ Prev")
				.callbackData("paging_" + query + "_" + branch + "_" + (currentPage - 1))
				.build());
		}

		row.add(InlineKeyboardButton.builder()
			.text(currentElement + " - " + maxElement + " / " + totalElements)
			.callbackData("noop")
			.build());

		if (page.hasNext()) {
			row.add(InlineKeyboardButton.builder()
				.text("Next ➡")
				.callbackData("paging_" + query + "_" + branch + "_" + (currentPage + 1))
				.build());
		}

		return row;
	}

	private List<InlineKeyboardRow> buildDataButtons(List<Bills> dataList, int currentPage, String query, String branch) {
		List<InlineKeyboardRow> rows = new ArrayList<>();
		InlineKeyboardRow currentRow = new InlineKeyboardRow();

		for (int i = 0; i < dataList.size(); i++) {
			Bills bill = dataList.get(i);

			currentRow.add(InlineKeyboardButton.builder()
				.text(bill.getName())
				.callbackData("tagihan_" + bill.getNoSpk() + "_" + query + "_" + branch + "_" + currentPage)
				.build());

			if (currentRow.size() == 2 || i == dataList.size() - 1) {
				rows.add(currentRow);
				currentRow = new InlineKeyboardRow();
			}
		}

		return rows;
	}
}
