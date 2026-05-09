package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.configuration.S3ClientConfiguration;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.service.slik.PDFReader;
import org.cekpelunasan.core.service.slik.SlikNameFormatter;
import org.cekpelunasan.core.service.slik.SlikSessionCache;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.utils.button.SlikButtonConfirmation;
import org.cekpelunasan.utils.button.SlikNamePaginationButton;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

/**
 * Handler untuk callback pemilihan bulan/tahun pada alur pencarian SLIK.
 *
 * <p>Callback {@code slikmn_YYYYMM} dikirim setelah user memilih bulan dari keyboard
 * yang ditampilkan oleh {@code SlikCommand}. Handler ini membaca pending query dari
 * {@link SlikSessionCache}, lalu melanjutkan alur sesuai tipe query:</p>
 * <ul>
 *   <li>{@code name} — cari PDF di folder bulan yang dipilih, tampilkan paginasi hasil</li>
 *   <li>{@code ktp} — tampilkan keyboard pilih jenis laporan (aktif / semua data)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlikMonthCallbackHandler extends AbstractCallbackHandler {

    private static final String KTP_PREFIX    = "KTP_";
    private static final String KTP_EXTENSION = ".txt";

    private static final String MSG_NO_SESSION   = "⚠️ Sesi pencarian sudah habis, ulangi `/slik <query>`";
    private static final String MSG_NO_RESULTS   = "❌ Tidak ada data SLIK ditemukan untuk bulan tersebut";
    private static final String MSG_UNKNOWN_USER = "❌ User tidak dikenali, hubungi Admin";
    private static final String MSG_PICK_REPORT  = "📋 Pilih jenis laporan SLIK:";

    private final SlikSessionCache      slikSessionCache;
    private final S3ClientConfiguration s3Connector;
    private final PDFReader             pdfReader;
    private final SlikNameFormatter     formatter;
    private final SlikNamePaginationButton paginationButton;
    private final SlikButtonConfirmation   buttonConfirmation;
    private final UserService           userService;

    @Override
    public String getCallBackData() {
        return "slikmn";
    }

    /**
     * Memproses pilihan bulan dari user.
     *
     * <p>Mengambil pending query dari session cache, menghapus pesan keyboard bulan,
     * lalu meneruskan ke pencarian nama atau keyboard pilih fasilitas sesuai tipe query.</p>
     */
    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String[] parts = callbackData.split("_");

        long chatId  = update.chatId;
        String yyyymm = parts.length >= 2 ? parts[1] : "";

        SlikSessionCache.PendingQuery pending = slikSessionCache.takePending(chatId);
        if (pending == null) {
            return Mono.<Void>fromRunnable(() ->
                telegramMessageService.sendText(chatId, MSG_NO_SESSION, client))
                .subscribeOn(Schedulers.boundedElastic());
        }

        telegramMessageService.delete(chatId, update.messageId, client);

        if ("ktp".equals(pending.type())) {
            log.info("SLIK month selected — chatId={} yyyymm={} type=ktp query={}", chatId, yyyymm, pending.query());
            return Mono.<Void>fromRunnable(() ->
                telegramMessageService.sendKeyboard(chatId,
                    buttonConfirmation.sendSlikCommandWithMonth(pending.query(), yyyymm),
                    client, MSG_PICK_REPORT))
                .subscribeOn(Schedulers.boundedElastic());
        }

        // name search: list YYYY_MM/pdf/ and filter by query
        String folder     = yyyymm.substring(0, 4) + "_" + yyyymm.substring(4);
        String prefix     = folder + "/pdf/";
        String queryLower = pending.query().toLowerCase();
        log.info("SLIK month selected — chatId={} yyyymm={} type=name query={}", chatId, yyyymm, pending.query());

        return userService.findUserByChatId(chatId)
            .flatMap(user -> {
                boolean isAdmin = user.getRoles() == AccountOfficerRoles.ADMIN
                    || user.getRoles() == AccountOfficerRoles.PIMP;
                String userCode = user.getUserCode();

                return s3Connector.listObjectFoundByName(prefix)
                    .filter(key -> !key.contains("KTP_")
                        && key.toLowerCase().contains(queryLower)
                        && (isAdmin || userCode == null || key.contains("/" + userCode + "_")))
                    .flatMap(contentKey -> extractPageData(contentKey, folder))
                    .collectList()
                    .flatMap(pages -> {
                        if (pages.isEmpty()) {
                            return Mono.<Void>fromRunnable(() ->
                                telegramMessageService.sendText(chatId, MSG_NO_RESULTS, client))
                                .subscribeOn(Schedulers.boundedElastic());
                        }
                        slikSessionCache.put(chatId, new SlikSessionCache.SlikSession(pages, pending.query()));
                        TdApi.FormattedText message  = formatter.format(pages.get(0), 0, pages.size());
                        TdApi.ReplyMarkupInlineKeyboard keyboard = paginationButton.build(0, pages.size());
                        log.info("SLIK name search — chatId={} folder={} results={}", chatId, folder, pages.size());
                        return Mono.<Void>fromRunnable(() ->
                            telegramMessageService.sendKeyboardFormatted(chatId, keyboard, client, message))
                            .subscribeOn(Schedulers.boundedElastic());
                    });
            })
            .switchIfEmpty(Mono.<Void>fromRunnable(() ->
                telegramMessageService.sendText(chatId, MSG_UNKNOWN_USER, client))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * Mengambil file PDF dari S3, mengekstrak nomor KTP dari isinya, lalu memuat
     * dan mem-parse file TXT SLIK yang bersesuaian.
     *
     * @param contentKey path S3 lengkap file PDF, mis. {@code 2026_05/pdf/SMG_budi.pdf}
     * @param folder     folder bulan, mis. {@code 2026_05} (untuk menemukan TXT yang sepadan)
     * @return {@link Mono} berisi {@link SlikSessionCache.SlikPageData}; selalu emit satu elemen
     */
    private Mono<SlikSessionCache.SlikPageData> extractPageData(String contentKey, String folder) {
        return s3Connector.getFile(contentKey)
            .flatMap(pdfBytes -> pdfReader.generateIDNumber(pdfBytes)
                .flatMap(idNumber -> {
                    String ktpKey = folder + "/txt/" + KTP_PREFIX + idNumber + KTP_EXTENSION;
                    return s3Connector.getFile(ktpKey)
                        .flatMap(txtBytes -> formatter.parse(txtBytes)
                            .map(dto -> new SlikSessionCache.SlikPageData(contentKey, idNumber, dto))
                            .defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, idNumber, null)))
                        .defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, idNumber, null));
                })
                .defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, null, null)))
            .defaultIfEmpty(new SlikSessionCache.SlikPageData(contentKey, null, null));
    }
}
