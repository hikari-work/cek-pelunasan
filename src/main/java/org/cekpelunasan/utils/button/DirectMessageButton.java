package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DirectMessageButton {

    private static final Logger log = LoggerFactory.getLogger(DirectMessageButton.class);

    public TdApi.ReplyMarkupInlineKeyboard selectServices(String query) {
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        List<TdApi.InlineKeyboardButton> currentRow = new ArrayList<>();
        List<String> services = List.of("Pelunasan", "Tabungan");

        for (String service : services) {
            log.info("Adding button: services_{}_{}", service, query);
            currentRow.add(tdButton(service, "services_" + service + "_" + query));

            if (currentRow.size() == 2) {
                rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
        }

        return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][]));
    }

    private static TdApi.InlineKeyboardButton tdButton(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
