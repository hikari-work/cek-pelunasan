package org.cekpelunasan.platform.telegram.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.utils.button.DirectMessageButton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class InteractWithOwnerHandler extends AbstractCommandHandler {

	private final AuthorizedChats authorizedChats;
	private final DirectMessageButton directMessageButton;

	@Value("${telegram.bot.owner}")
	private Long ownerId;

	@Override
	public String getCommand() {
		return "/id";
	}

	@Override
	public String getDescription() {
		return "Gunakan command ini untuk generate User Id anda untuk kebutuhan Authorization";
	}

	@Override
	@Async
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {
			String text = update.getMessage().getText();
			Long chatId = update.getMessage().getChatId();
			Integer messageId = update.getMessage().getMessageId();

			if (text.equals(getCommand())) {
				sendMessage(chatId, "ID Kamu `" + chatId + "`", telegramClient);
				return;
			}

			if (authorizedChats.isAuthorized(chatId) && isValidAccount(text)) {
				sendMessage(chatId, "Pilih salah satu action dibawah ini", directMessageButton.selectServices(text.trim()), telegramClient);
				return;
			}

			if (!chatId.equals(ownerId)) {
				forwardMessage(chatId, ownerId, messageId, telegramClient);
				return;
			}

			Long originalUserId = update.getMessage().getReplyToMessage().getForwardFrom().getId();
			copyMessage(ownerId, messageId, originalUserId, telegramClient);
		});
	}

	private boolean isValidAccount(String input) {
		Matcher matcher = Pattern.compile("\\d{12}").matcher(input);
		return matcher.find();
	}
}
