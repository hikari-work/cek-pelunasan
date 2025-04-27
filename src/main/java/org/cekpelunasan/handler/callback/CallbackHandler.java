package org.cekpelunasan.handler.callback;

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
public class CallbackHandler {

    private final Map<String, CallbackProcessor> processorMap;

    @Autowired
    CallbackHandler(List<CallbackProcessor> processorList) {
        this.processorMap = processorList.stream().collect(Collectors.toMap(CallbackProcessor::getCallBackData, c -> c));
    }
    @Async
    @SuppressWarnings("unused")
    public CompletableFuture<Void> handle(Update update, TelegramClient telegramClient) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData().split("_")[0];
            System.out.println(update.getCallbackQuery().getData());
            CallbackProcessor callbackProcessor = processorMap.getOrDefault(callbackData, processorMap.get("none"));
            callbackProcessor.process(update, telegramClient);
        }
        return CompletableFuture.completedFuture(null);
    }

}
