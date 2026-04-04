package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import java.util.concurrent.CompletableFuture;

public interface CallbackProcessor {

    String getCallBackData();

    CompletableFuture<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client);
}
