package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CallbackHandler {

    private final Map<String, CallbackProcessor> processorMap;

    @Autowired
    CallbackHandler(List<CallbackProcessor> processorList) {
        this.processorMap = processorList.stream()
            .collect(Collectors.toMap(CallbackProcessor::getCallBackData, c -> c));
    }

    @Async
    public void handle(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        if (update.payload instanceof TdApi.CallbackQueryPayloadData payloadData) {
            String callbackData = new String(payloadData.data, StandardCharsets.UTF_8);
            String prefix = callbackData.split("_")[0];
            CallbackProcessor processor = processorMap.getOrDefault(prefix, processorMap.get("none"));
            if (processor != null) {
                processor.process(update, client).subscribe();
            }
        }
    }

}
