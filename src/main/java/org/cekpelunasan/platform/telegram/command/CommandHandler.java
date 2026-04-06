package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommandHandler {

    private final Map<String, CommandProcessor> processorMap;
    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    public CommandHandler(List<CommandProcessor> processorList) {
        this.processorMap = processorList.stream()
            .collect(Collectors.toMap(CommandProcessor::getCommand, p -> p));
    }

    @Async
    public void handle(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        try {
            if (!(update.message.content instanceof TdApi.MessageText messageText)) {
                return;
            }
            String text = messageText.text.text;
            String command = text.split(" ")[0];

            CommandProcessor processor = processorMap.get(command);
            if (processor == null) {
                processor = processorMap.get("/id");
            }
            if (processor != null) {
                processor.process(update, client).subscribe();
            }
        } catch (Exception e) {
            log.error("ERROR in handle: ", e);
        }
    }
}
