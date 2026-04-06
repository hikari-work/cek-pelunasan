package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Membuat tombol-tombol bantuan (help) yang menampilkan web app preview untuk setiap fitur utama.
 * <p>
 * Ketika pengguna mengirim perintah help, bot menampilkan keyboard berisi tombol
 * yang membuka tampilan web app dari fitur-fitur utama: Pelunasan, Tagihan,
 * Tabungan, dan Kolek Tas. URL tujuan dikonfigurasi lewat property {@code webapp.base-url}.
 * </p>
 */
@Component
public class HelpButton {
    private static final Logger log = LoggerFactory.getLogger(HelpButton.class);

    @Value("${webapp.base-url:http://localhost:8080}")
    private String webAppBaseUrl;

    /**
     * Membuat keyboard inline berisi empat tombol web app untuk melihat tampilan setiap fitur.
     * Tombol menggunakan tipe WebApp sehingga membuka halaman web langsung di dalam Telegram.
     *
     * @return keyboard inline dengan tombol Pelunasan, Tagihan, Tabungan, dan Kolek Tas
     */
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
