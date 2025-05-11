package org.cekpelunasan.utils.button;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class DirectMessageButton {

	public InlineKeyboardMarkup selectServices(String query) {
		List<InlineKeyboardRow> rows = new ArrayList<>();
		InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();
		List<String> services = List.of("Pelunasan", "Tabungan");

		for (String service : services) {
			InlineKeyboardButton button = InlineKeyboardButton.builder()
				.text(service)
				.callbackData("services_" + service + "_" + query)
				.build();
			inlineKeyboardButtons.add(button);

			if (inlineKeyboardButtons.size() == 2) {
				rows.add(inlineKeyboardButtons);
				inlineKeyboardButtons = new InlineKeyboardRow();
			}
		}

		// Add the remaining buttons if any
		if (!inlineKeyboardButtons.isEmpty()) {
			rows.add(inlineKeyboardButtons);
		}

		return InlineKeyboardMarkup.builder()
			.keyboard(rows)
			.build();
	}
}