package org.cekpelunasan.utils.button;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.Collections;
import java.util.List;

public class BackKeyboardUtils {

	public InlineKeyboardMarkup backButton(String query) {
		String[] parts = query.split("_");

		if (parts.length < 4) {
			throw new IllegalArgumentException("Invalid query format: " + query);
		}

		String customerId = parts[1];
		String queryName = parts[2];
		int page = Integer.parseInt(parts[3]);

		InlineKeyboardRow row = new InlineKeyboardRow(List.of(
						buildBackButton(queryName, page),
						buildPhotoButton(customerId)
		));

		return InlineKeyboardMarkup.builder()
						.keyboard(Collections.singletonList(row))
						.build();
	}

	private InlineKeyboardButton buildBackButton(String queryName, int page) {
		return InlineKeyboardButton.builder()
						.text("🔙 Kembali")
						.callbackData("page_" + queryName + "_" + page)
						.build();
	}

	private InlineKeyboardButton buildPhotoButton(String customerId) {
		return InlineKeyboardButton.builder()
						.text("📸 Kirim Gambar")
						.callbackData("photo_" + customerId)
						.build();
	}
}
