package org.cekpelunasan.utils.button;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public class BackKeyboardButtonForBillsUtils {

	public InlineKeyboardMarkup backButton(String query) {
		String[] parts = query.split("_");
		String name = parts[2];
		String branch = parts[3];
		String page = parts[4];

		List<InlineKeyboardRow> inlineKeyboardRows = new ArrayList<>();
		InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();

		InlineKeyboardButton backButton = InlineKeyboardButton.builder()
						.text("◀️ Kembali")
						.callbackData("paging_" + name + "_" + branch + "_" + page)
						.build();
		inlineKeyboardButtons.add(backButton);
		inlineKeyboardRows.add(inlineKeyboardButtons);
		return InlineKeyboardMarkup.builder()
						.keyboard(inlineKeyboardRows)
						.build();
	}
}
