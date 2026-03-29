package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HelpButton {
    private static final Logger log = LoggerFactory.getLogger(HelpButton.class);

    @Value("${webapp.base-url:http://localhost:8080}")
    private String webAppBaseUrl;

    public TdApi.ReplyMarkupInlineKeyboard sendHelpMessage() {
        TdApi.InlineKeyboardButton repaymentButton = webAppButton("📸 Tampilan Pelunasan", webAppBaseUrl + "/pelunasan");
        TdApi.InlineKeyboardButton billsButton = webAppButton("📸 Tampilan Tagihan", webAppBaseUrl + "/tagihan");
        TdApi.InlineKeyboardButton savingsButton = webAppButton("📸 Tampilan Tabungan", webAppBaseUrl + "/tabungan");
        TdApi.InlineKeyboardButton kolekTasButton = webAppButton("📸 Tampilan Kolek Tas", webAppBaseUrl + "/kolektas");
        log.info("Created Help Buttons");
        TdApi.InlineKeyboardButton[][] rows = {
            {repaymentButton},
            {billsButton},
            {savingsButton},
            {kolekTasButton}
        };
        return new TdApi.ReplyMarkupInlineKeyboard(rows);
    }

    private static TdApi.InlineKeyboardButton webAppButton(String text, String url) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeWebApp type = new TdApi.InlineKeyboardButtonTypeWebApp();
        type.url = url;
        btn.type = type;
        return btn;
    }
}
