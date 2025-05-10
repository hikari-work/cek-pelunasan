package org.cekpelunasan.handler.command.template;

import org.cekpelunasan.bot.TelegramBot;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GenerateHelpMessage {

	private final Map<String, CommandProcessor> commandMap = new HashMap<>();
	@Value("${telegram.bot.owner}")
	private Long ownerId;
	@Value("${telegram.bot.token}")
	private String botToken;

	public GenerateHelpMessage(List<CommandProcessor> processors, TelegramBot telegramBot) {
		for (CommandProcessor processor : processors) {
			commandMap.put(processor.getCommand(), processor);
		}
	}

	public String generateHelpText() {
		StringBuilder stringBuilder = new StringBuilder("*List Command*:\n\n");
		for (CommandProcessor cp : commandMap.values()) {
			stringBuilder.append(cp.getCommand()).append(" - ").append(cp.getDescription()).append("\n");

		}
		return stringBuilder.toString();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void generateHelpMessage() {
		TelegramClient telegramClient = new OkHttpTelegramClient(botToken);
		try {
			telegramClient.execute(SendMessage.builder()
							.chatId(ownerId)
							.text(generateHelpText())
							.build());
		} catch (TelegramApiException e) {
			throw new RuntimeException(e);
		}
	}


}
