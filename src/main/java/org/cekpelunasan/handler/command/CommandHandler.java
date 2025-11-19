package org.cekpelunasan.handler.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommandHandler {

	private final TelegramClient telegramClient;
	private final Map<String, CommandProcessor> processorMap;
	private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

	public CommandHandler(TelegramClient telegramClient, List<CommandProcessor> processorList) {
		this.telegramClient = telegramClient;
		this.processorMap = processorList.stream()
			.collect(Collectors.toMap(CommandProcessor::getCommand, p -> p));
	}

	@Async
	public void handle(Update update) {
		try {
			if (update.getMessage() == null || update.getMessage().getText() == null) {
				return;
			}

			String messageText = update.getMessage().getText();
			String command = messageText.split(" ")[0];

			CommandProcessor commandProcessor = processorMap.get(command);

			if (commandProcessor == null) {
				commandProcessor = processorMap.get("/id");
			}

			if (commandProcessor != null) {
				commandProcessor.process(update, telegramClient);
			}
		} catch (Exception e) {
			log.error("ERROR in handle: ", e);
		}
	}
}