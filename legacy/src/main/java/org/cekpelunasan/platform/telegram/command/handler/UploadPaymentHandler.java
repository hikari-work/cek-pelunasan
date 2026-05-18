package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.event.DatabaseUpdateEvent;
import org.cekpelunasan.core.event.EventType;
import org.cekpelunasan.core.service.paymentdetails.PaymentDetailsService;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.platform.telegram.service.UploadProgressService;
import org.cekpelunasan.utils.CsvDownloadUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadPaymentHandler extends AbstractCommandHandler {

    private static final String ERROR_FORMAT = "❗ *Format salah.*\nGunakan `/uploadpayment <link_csv>`";

    private final ApplicationEventPublisher publisher;
    private final PaymentDetailsService paymentDetailsService;
    private final UploadProgressService progressService;

    @Override
    public String getCommand() {
        return "/uploadpayment";
    }

    @Override
    public String getDescription() {
        return "Upload dan proses file CSV data pembayaran angsuran AO";
    }

    @Override
    @RequireAuth(roles = AccountOfficerRoles.ADMIN)
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String fileUrl = CsvDownloadUtils.extractUrl(text);
        if (fileUrl == null) {
            return Mono.fromRunnable(() -> sendMessage(chatId, ERROR_FORMAT, client));
        }
        return Mono.fromCallable(() -> CsvDownloadUtils.downloadCsv(fileUrl))
            .flatMap(filePath -> {
                long total = progressService.countLines(filePath);
                long[] msgIdRef = {progressService.sendProgressMessage(chatId, "Data Payment", total, client)};
                return paymentDetailsService.parseCsvAndSave(filePath, total,
                    done -> progressService.updateProgress(chatId, msgIdRef[0], "Data Payment", done, total, client));
            })
            .doOnSuccess(v -> {
                log.info("File Payment Details berhasil diproses: {}", CsvDownloadUtils.extractFileName(fileUrl));
                publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.PAYMENT_DETAILS, true));
                sendMessage(chatId, "✅ Data payment berhasil diimpor", client);
            })
            .onErrorResume(e -> {
                log.error("Gagal memproses file Payment Details dari URL: {}", fileUrl, e);
                publisher.publishEvent(new DatabaseUpdateEvent(this, EventType.PAYMENT_DETAILS, false));
                sendMessage(chatId, "❌ Gagal memproses file: " + e.getMessage(), client);
                return Mono.empty();
            })
            .then();
    }
}
