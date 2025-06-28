package org.cekpelunasan.bot;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.handler.callback.CallbackHandler;
import org.cekpelunasan.handler.command.CommandHandler;
import org.cekpelunasan.handler.command.handler.PengakuanTransferHandle;
import org.cekpelunasan.handler.inline.GeminiServiceAnswer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@RequiredArgsConstructor
public class TelegramBot {

	private final CommandHandler commandHandler;
	private final CallbackHandler callbackHandler;
	private final GeminiServiceAnswer geminiServiceAnswer;
	private final PengakuanTransferHandle pengakuanTransferHandle;

	@Async
	public void startBot(Update update, TelegramClient telegramClient) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			commandHandler.handle(update, telegramClient);
			return;
		}
		if (update.hasCallbackQuery()) {
			callbackHandler.handle(update, telegramClient);
			return;
		}
		if (update.hasInlineQuery()) {
			geminiServiceAnswer.handleInlineQuery(update.getInlineQuery(), telegramClient);
			return;
		}
		if (update.getMessage().hasPhoto()) {
			pengakuanTransferHandle.handle(update, telegramClient);
			return;
		}

	}
}
