package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.auth.AuthorizedChats;
import org.cekpelunasan.utils.button.DirectMessageButton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class InteractWithOwnerHandler extends AbstractCommandHandler {

    private final AuthorizedChats authorizedChats;
    private final DirectMessageButton directMessageButton;

    @Value("${telegram.bot.owner}")
    private Long ownerId;

    @Override
    public String getCommand() {
        return "/id";
    }

    @Override
    public String getDescription() {
        return "Gunakan command ini untuk generate User Id anda untuk kebutuhan Authorization";
    }

    @Override
    @Async
    public CompletableFuture<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return CompletableFuture.runAsync(() -> {
            if (!(update.message.content instanceof TdApi.MessageText messageText)) {
                return;
            }
            String text = messageText.text.text;
            long chatId = update.message.chatId;
            long messageId = update.message.id;

            if (text.equals(getCommand())) {
                sendMessage(chatId, "ID Kamu `" + chatId + "`", client);
                return;
            }

            if (authorizedChats.isAuthorized(chatId) && isValidAccount(text)) {
                sendMessage(chatId, "Pilih salah satu action dibawah ini", directMessageButton.selectServices(text.trim()), client);
                return;
            }

            if (chatId != ownerId) {
                forwardMessage(chatId, ownerId, messageId, client);
                return;
            }

            if (update.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
                // TODO: In TDLight, retrieving forwardFrom requires GetMessage RPC call
                // log.warn("Forward-back to original sender not yet implemented in TDLight");
                copyMessage(ownerId, replyTo.messageId, ownerId, client);
            }
        });
    }

    private boolean isValidAccount(String input) {
        Matcher matcher = Pattern.compile("\\d{12}").matcher(input);
        return matcher.find();
    }
}
