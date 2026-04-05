package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocCommand extends AbstractCommandHandler {

    private final S3ClientConfiguration s3Connector;
    private final UserService userService;

    @Override
    public String getCommand() {
        return "/doc";
    }

    @Override
    public String getDescription() {
        return "Download file SLIK asli dari S3 berdasarkan nama file";
    }

    @Override
    @RequireAuth(roles = { AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN, AccountOfficerRoles.PIMP })
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String[] parts = text.split(" ", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return Mono.fromRunnable(() ->
                sendMessage(chatId, "Gunakan: `/doc <nama file>`", client));
        }

        String fileName = parts[1].trim();

        return userService.findUserByChatId(chatId)
            .flatMap(user -> {
                boolean isAdmin = AccountOfficerRoles.ADMIN == user.getRoles();
                if (!isAdmin && (user.getUserCode() == null || !fileName.startsWith(user.getUserCode() + "_"))) {
                    sendMessage(chatId, "❌ Anda tidak memiliki akses ke file ini", client);
                    return Mono.<Void>empty();
                }
                return downloadAndSend(chatId, fileName, client);
            })
            .switchIfEmpty(Mono.defer(() -> downloadAndSend(chatId, fileName, client)));
    }

    private Mono<Void> downloadAndSend(long chatId, String fileName, SimpleTelegramClient client) {
        log.info("Downloading S3 file: {} for chatId: {}", fileName, chatId);
        return s3Connector.getFile(fileName)
            .flatMap(bytes -> {
                sendDocument(chatId, fileName, bytes, client);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.fromRunnable(() ->
                sendMessage(chatId, "❌ File tidak ditemukan: `" + fileName + "`", client)));
    }
}
