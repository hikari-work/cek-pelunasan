package org.cekpelunasan.handler.callback.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.handler.callback.CallbackProcessor;
import org.cekpelunasan.service.repayment.RepaymentService;
import org.cekpelunasan.utils.ImageGeneratorUtils;
import org.cekpelunasan.utils.PenaltyUtils;
import org.cekpelunasan.utils.RupiahFormatUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PhotoHandler implements CallbackProcessor {


	private final RepaymentService repaymentService;

	@Override
	public String getCallBackData() {
		return "photo";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			log.info("Generating Photo For Pelunasan...");
			String data = update.getCallbackQuery().getData();
			String[] parts = data.split("_");
			String customerId = parts[1];
			Repayment repayment = repaymentService.findRepaymentById(Long.parseLong(customerId));
			ImageGeneratorUtils generatorUtils = new ImageGeneratorUtils(new RupiahFormatUtils(), new PenaltyUtils());
			InputFile inputFile = generatorUtils.generateImages(repayment);
			long chatId = update.getCallbackQuery().getMessage().getChatId();
			sendPhoto(chatId, "Done", inputFile, telegramClient);


		});
	}
}
