package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Membuat tombol pemilihan layanan untuk fitur direct message (pesan langsung ke nasabah).
 * <p>
 * Ketika admin ingin mengirim pesan langsung ke nasabah, ditampilkan pilihan
 * jenis layanan yang ingin dicek: Pelunasan atau Tabungan. Class ini menyusun
 * tombol-tombol tersebut secara dinamis dengan data callback yang membawa konteks nomor nasabah.
 * </p>
 */
@Component
public class DirectMessageButton {

    private static final Logger log = LoggerFactory.getLogger(DirectMessageButton.class);

    /**
     * Membuat keyboard inline berisi pilihan layanan (Pelunasan dan Tabungan).
     * Setiap tombol menyimpan data callback "services_[nama layanan]_[query]"
     * sehingga handler tahu layanan apa yang dipilih dan untuk nomor nasabah mana.
     *
     * @param query nomor akun atau konteks pencarian yang akan dibawa ke langkah berikutnya
     * @return keyboard inline berisi pilihan layanan
     */
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
