package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.event.DatabaseUpdateEvent;
import org.cekpelunasan.core.event.EventType;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillsUpdateDataHandler extends AbstractCommandHandler {

    private static final String PROCESSING_MESSAGE = "⏳ *Sedang mengunduh dan memproses file...*";
    private static final String ERROR_DOWNLOAD = "❌ Gagal memproses file";

    private final BillService billService;
    private final ApplicationEventPublisher publisher;

    @Override
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public String getCommand() {
        return "/uploadtagihan";
    }

    @Override
    public String getDescription() {
        return "Gunakan Command ini untuk upload data tagihan harian.";
    }

    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String fileUrl = CsvDownloadUtils.extractUrl(text);
        if (fileUrl == null) {
            log.warn("Invalid format from chat {}", chatId);
            return Mono.empty();
        }
        sendMessage(chatId, PROCESSING_MESSAGE, client);
        log.info("Command: /uploadtagihan Executed with file: {}", CsvDownloadUtils.extractFileName(fileUrl));
        return Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
            .flatMap(filePath -> billService.parseCsvAndSaveIntoDatabase(filePath))
            .doOnSuccess(v -> publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, true)))
            .onErrorResume(e -> {
                log.error("Error processing CSV file: {}", fileUrl, e);
                sendMessage(chatId, ERROR_DOWNLOAD, client);
                publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.TAGIHAN, false));
                return Mono.empty();
            });
    }
}
