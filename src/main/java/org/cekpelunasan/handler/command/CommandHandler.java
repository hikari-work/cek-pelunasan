package org.cekpelunasan.handler.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class CommandHandler {

	private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
	private final Map<String, CommandProcessor> processor;

	@Autowired
	CommandHandler(List<CommandProcessor> processorList) {
		this.processor = processorList.stream().collect(Collectors.toMap(CommandProcessor::getCommand, p -> p));
	}

	@Async
	public void handle(Update update, TelegramClient telegramClient) {
		String command = update.getMessage().getText().split(" ")[0];
		log.info("Get message {}", update.getMessage().getText());
		CommandProcessor commandProcessor = processor.getOrDefault(command, processor.get("/id"));
		commandProcessor.process(update.getMessage().getChatId(), update.getMessage().getText(), telegramClient);
		commandProcessor.process(update, telegramClient);
		CompletableFuture.completedFuture(null);

	}


}
