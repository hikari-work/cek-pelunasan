package org.cekpelunasan.handler.command;

import lombok.RequiredArgsConstructor;
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
		log.info("Received update: {}", telegramClient.toString());
		log.info("=== HANDLE START ===");
		try {
			log.info("Step 1: Checking update message");
			if (update.getMessage() == null || update.getMessage().getText() == null) {
				log.warn("Received update with null message or text");
				return;
			}

			String messageText = update.getMessage().getText();
			String command = messageText.split(" ")[0];

			log.info("Step 2: Processing command: {}, full message: {}", command, messageText);
			log.info("Step 3: Available processors: {}", processorMap.keySet());

			CommandProcessor commandProcessor = processorMap.get(command);
			log.info("Step 4: Processor found: {}", commandProcessor != null);

			if (commandProcessor == null) {
				log.info("Step 5: Trying fallback /id");
				commandProcessor = processorMap.get("/id");
				log.info("Step 5a: Fallback processor found: {}", commandProcessor != null);
			}

			if (commandProcessor != null) {
				log.info("Step 6: Executing processor: {}", commandProcessor.getClass().getSimpleName());
				commandProcessor.process(update, telegramClient);
				log.info("Step 7: Command processed successfully");
			} else {
				log.error("Step 8: No processor found for command: {} and fallback /id is not registered", command);
			}
			log.info("=== HANDLE END ===");
		} catch (Exception e) {
			log.error("ERROR in handle: ", e);
			e.printStackTrace();
		}
	}
}