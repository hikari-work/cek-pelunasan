package org.cekpelunasan.utils.button;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ButtonListForSelectBranch {

	public InlineKeyboardMarkup dynamicSelectBranch(Set<String> branchName, String query) {

		List<InlineKeyboardRow> rows = new ArrayList<>();
		InlineKeyboardRow currentRow = new InlineKeyboardRow();
		List<String> branchList = new ArrayList<>(branchName);

		for (int i = 0; i < branchList.size(); i++) {
			InlineKeyboardButton button = InlineKeyboardButton.builder()
				.text(branchList.get(i))
				.callbackData("branch_" + branchList.get(i) + "_" + query)
				.build();
			currentRow.add(button);
			if (currentRow.size() == 3 || i == branchList.size() - 1) {
				rows.add(currentRow);
				currentRow = new InlineKeyboardRow();
			}

		}
		return InlineKeyboardMarkup.builder()
			.keyboard(rows)
			.build();
	}
}
