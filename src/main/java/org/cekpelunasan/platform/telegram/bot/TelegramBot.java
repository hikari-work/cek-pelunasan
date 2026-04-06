package org.cekpelunasan.platform.telegram.bot;

import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.CallbackHandler;
import org.cekpelunasan.platform.telegram.command.CommandHandler;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot implements ApplicationListener<ApplicationReadyEvent> {

    private final SimpleTelegramClientFactory factory;
    private final TDLibSettings settings;
    private final CommandHandler commandHandler;
    private final CallbackHandler callbackHandler;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Getter
	private SimpleTelegramClient client;

    @Override
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        try {
            log.info("Starting TDLight client...");
            SimpleTelegramClientBuilder builder = factory.builder(settings);
            builder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onMessage);
            builder.addUpdateHandler(TdApi.UpdateNewCallbackQuery.class, this::onCallbackQuery);
            builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, update ->
                log.info("TDLight auth state: {}", update.authorizationState.getClass().getSimpleName()));
            client = builder.build(AuthenticationSupplier.bot(botToken));
            log.info("TDLight client started.");
        } catch (Exception e) {
            log.error("Failed to start TDLight client", e);
        }
    }

    private void onMessage(TdApi.UpdateNewMessage update) {
        if (update.message.content instanceof TdApi.MessageText) {
            commandHandler.handle(update, client);
        }
    }

    private void onCallbackQuery(TdApi.UpdateNewCallbackQuery update) {
        callbackHandler.handle(update, client);
    }

	@PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
                log.info("TDLight client closed.");
            } catch (Exception e) {
                log.warn("Error closing TDLight client", e);
            }
        }
    }
}
