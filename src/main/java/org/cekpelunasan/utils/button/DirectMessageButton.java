package org.cekpelunasan.utils.button;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class DirectMessageButton {

	private static final Logger log = LoggerFactory.getLogger(DirectMessageButton.class);

	public InlineKeyboardMarkup selectServices(String query) {
		List<InlineKeyboardRow> rows = new ArrayList<>();
		InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();
		List<String> services = List.of("Pelunasan", "Tabungan");

		for (String service : services) {
			log.info("Adding button: services_{}_{}", service, query);
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