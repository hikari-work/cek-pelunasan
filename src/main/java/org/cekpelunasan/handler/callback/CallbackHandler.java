package org.cekpelunasan.handler.callback;

import org.cekpelunasan.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CallbackHandler {

    private final Map<String, CallbackProcessor> processorMap;
    private final UserService userService;
    private final List<Long> authorizedChats;

    @Autowired
    CallbackHandler(List<CallbackProcessor> processorList, UserService userService, List<Long> authorizedChats) {
        this.processorMap = processorList.stream().collect(Collectors.toMap(CallbackProcessor::getCallBackData, c -> c));
        this.userService = userService;
        this.authorizedChats = authorizedChats;
    }
    public void handle(Update update, TelegramClient telegramClient) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData().split("_")[0];
            CallbackProcessor callbackProcessor = processorMap.getOrDefault(callbackData, processorMap.get("none"));
            callbackProcessor.process(update, telegramClient);
        }
    }

}
