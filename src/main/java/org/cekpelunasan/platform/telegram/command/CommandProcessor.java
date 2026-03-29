package org.cekpelunasan.platform.telegram.command;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import java.util.concurrent.CompletableFuture;

public interface CommandProcessor {

    String getCommand();

    String getDescription();

    CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client);

    default CompletableFuture<Void> process(long chatId, String text, SimpleTelegramClient client) {
        return CompletableFuture.completedFuture(null);
    }
}
