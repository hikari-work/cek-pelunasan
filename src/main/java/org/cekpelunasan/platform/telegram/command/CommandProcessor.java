package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import reactor.core.publisher.Mono;

public interface CommandProcessor {

    String getCommand();

    String getDescription();

    Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client);

    default Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        return Mono.empty();
    }
}
