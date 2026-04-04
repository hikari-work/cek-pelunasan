package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NoContextCallbackHandler extends AbstractCallbackHandler {
    @Override
    public String getCallBackData() {
        return "noop";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        return Mono.fromRunnable(() -> {
            log.info("Someone Makes Mistakes...");
            TdApi.AnswerCallbackQuery answer = new TdApi.AnswerCallbackQuery();
            answer.callbackQueryId = update.id;
            answer.text = "🐊 Pap Dulu Dong Maniess";
            answer.showAlert = true;
            client.send(answer, r -> {});
        });
    }
}
