package org.cekpelunasan.platform.telegram.callback;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import reactor.core.publisher.Mono;

public interface CallbackProcessor {

    String getCallBackData();

    Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client);
}
