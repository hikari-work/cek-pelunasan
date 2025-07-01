package org.cekpelunasan.utils.button;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Component
public class SlikButtonConfirmation {

	public InlineKeyboardMarkup sendSlikCommand(String query) {
		List<InlineKeyboardRow> rows = new ArrayList<>();
		InlineKeyboardRow inlineKeyboardButtons = new InlineKeyboardRow();

		InlineKeyboardButton aktifButton = InlineKeyboardButton.builder()
			.text("Kirim Data Fasilitas Aktif")
			.callbackData("slik_" + query + "_1")
			.build();
		InlineKeyboardButton allButton = InlineKeyboardButton.builder()
			.text("Kirim Semua Data")
			.callbackData("slik_" + query +"_0")
			.build();

		inlineKeyboardButtons.add(aktifButton);
		inlineKeyboardButtons.add(allButton);
		rows.add(inlineKeyboardButtons);
		return InlineKeyboardMarkup.builder()
			.keyboard(rows)
			.build();
	}
}
