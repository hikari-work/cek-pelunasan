package org.cekpelunasan.utils.button;

import org.cekpelunasan.service.NgrokService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.ArrayList;
import java.util.List;

@Component
public class HelpButton {
	private final NgrokService ngrokService;

	public HelpButton(NgrokService ngrokService) {
		this.ngrokService = ngrokService;
	}

public InlineKeyboardMarkup sendHelpMessage() {
	List<InlineKeyboardRow> rows = new ArrayList<>();
	String ngrokUrl = ngrokService.extractPublicUrl(ngrokService.getTunnelInfo());

	InlineKeyboardButton repaymentButton = InlineKeyboardButton.builder()
		.text("📸 Tampilan Pelunasan")
		.webApp(new WebAppInfo(ngrokUrl+ "/pelunasan"))
		.build();
	InlineKeyboardButton billsButton = InlineKeyboardButton.builder()
		.text("📸 Tampilan Tagihan")
		.webApp(new WebAppInfo(ngrokUrl+ "/tagihan"))
		.build();
	InlineKeyboardButton savingsButton = InlineKeyboardButton.builder()
		.text("📸 Tampilan Tabungan")
		.webApp(new WebAppInfo(ngrokUrl+ "/tabungan"))
		.build();
	InlineKeyboardButton kolekTasButton = InlineKeyboardButton.builder()
		.text("📸 Tampilan Kolek Tas")
		.webApp(new WebAppInfo(ngrokUrl+ "/kolektas"))
		.build();
	
	rows.add(new InlineKeyboardRow(repaymentButton));
	rows.add(new InlineKeyboardRow(billsButton));
	rows.add(new InlineKeyboardRow(savingsButton));
	rows.add(new InlineKeyboardRow(kolekTasButton));
	
	return InlineKeyboardMarkup.builder()
		.keyboard(rows)
		.build();
}

}