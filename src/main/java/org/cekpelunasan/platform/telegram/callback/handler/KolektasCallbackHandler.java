package org.cekpelunasan.platform.telegram.callback.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.callback.AbstractCallbackHandler;
import org.cekpelunasan.platform.telegram.callback.pagination.PaginationKolekTas;
import org.cekpelunasan.core.service.kolektas.KolekTasService;
import org.cekpelunasan.utils.KolekTasUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
@Component
public class KolektasCallbackHandler extends AbstractCallbackHandler {

    private final KolekTasUtils kolekTasUtils;
    private final PaginationKolekTas paginationKolekTas;
    private final KolekTasService kolekTasService;

    @Override
    public String getCallBackData() {
        return "koltas";
    }

    @Override
    public Mono<Void> process(TdApi.UpdateNewCallbackQuery update, SimpleTelegramClient client) {
        log.info("Kolektas Received....");
        String callbackData = new String(((TdApi.CallbackQueryPayloadData) update.payload).data, StandardCharsets.UTF_8);
        String data = callbackData.split("_")[1].trim().toLowerCase();
        long chatId = update.chatId;
        int page = Integer.parseInt(callbackData.split("_")[2]);
        long messageId = update.messageId;

        if (data.isEmpty()) {
            log.info("Kolektas Parsing Text Is Not Successfull....");
            return Mono.fromRunnable(() -> sendMessage(chatId, "Data Tidak Boleh Kosong", client));
        }
        if (isValidKelompok(data)) {
            log.info("Group Is Not Valid");
            return Mono.fromRunnable(() -> sendMessage(chatId, "Data Tidak Valid", client));
        }

        return kolekTasService.findKolekByKelompok(data, page + 1, 5)
            .flatMap(kolek -> Mono.fromRunnable(() -> {
                StringBuilder stringBuilder = new StringBuilder();
                log.info("Sending Kolek Tas For Group {}", data);
                kolek.forEach(k -> stringBuilder.append(kolekTasUtils.buildKolekTas(k)));
                TdApi.ReplyMarkupInlineKeyboard markup = paginationKolekTas.dynamicButtonName(kolek, page, data);
                editMessageWithMarkup(chatId, messageId, stringBuilder.toString(), client, markup);
            }))
            .then();
    }

    private boolean isValidKelompok(String text) {
        return text.matches("^[a-zA-Z]{3}\\.\\d+$");
    }
}
