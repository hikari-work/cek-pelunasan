package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.handler.command.template.MessageTemplate;
import org.cekpelunasan.service.auth.AuthorizedChats;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.utils.PenaltyUtils;
import org.cekpelunasan.utils.RepaymentCalculator;
import org.cekpelunasan.utils.button.SendPhotoKeyboard;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PelunasanCommandHandler implements CommandProcessor {

	private final RepaymentService repaymentService;
	private final AuthorizedChats authService;
	private final MessageTemplate messageTemplateService;
	private final RepaymentCalculator repaymentCalculator;

	@Override
	public String getCommand() {
		return "/pl";
	}

	@Override
	public String getDescription() {
		return """
			Gunakan command ini untuk menghitung pelunasan
			berdasarkan ID SPK yang anda kirimkan
			""";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			if (text == null) return;

			String message = text.trim();

			if (!authService.isAuthorized(chatId)) {
				sendMessage(chatId, messageTemplateService.unathorizedMessage(), telegramClient);
				return;
			}

			String[] tokens = message.split("\\s+");
			if (tokens.length < 2) {
				sendMessage(chatId, """
					‼ *Informasi* ‼
					
					Gunakan `/pl <No SPK>` untuk mencari SPK dan melakukan penghitungan Pelunasan.
					""", telegramClient);
				return;
			}

			try {
				Long customerId = Long.parseLong(tokens[1]);
				Repayment repayment = repaymentService.findRepaymentById(customerId);

				if (repayment == null) {
					sendMessage(chatId, "❌ Data Tidak Ditemukan", telegramClient);
					return;
				}

				Map<String, Long> penalty = new PenaltyUtils().penalty(
					repayment.getStartDate(),
					repayment.getPenaltyLoan(),
					repayment.getProduct(),
					repayment
				);

				String result = repaymentCalculator.calculate(repayment, penalty);

				sendMessage(chatId, result, telegramClient, new SendPhotoKeyboard().sendPhotoButton(customerId));

			} catch (NumberFormatException e) {
				sendMessage(chatId, "❗ *Format ID tidak valid*", telegramClient);
			}
		});
	}

	@Override
	public void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId.toString())
				.text(text)
				.parseMode("Markdown")
				.build());
		} catch (Exception e) {
			log.error("Error Sending Message", e);
		}
	}

	public void sendMessage(Long chatId, String text, TelegramClient telegramClient, InlineKeyboardMarkup markup) {
		try {
			telegramClient.execute(SendMessage.builder()
				.chatId(chatId.toString())
				.text(text)
				.replyMarkup(markup)
				.parseMode("Markdown")
				.build());
		} catch (Exception e) {
			log.error("Error");
		}
	}
}
