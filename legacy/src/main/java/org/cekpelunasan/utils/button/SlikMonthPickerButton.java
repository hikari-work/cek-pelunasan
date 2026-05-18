package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Membangun keyboard inline untuk memilih bulan dan tahun sebelum pencarian SLIK.
 *
 * <p>Menampilkan N bulan terakhir (default 12) dalam format tombol 3 per baris,
 * urutan terbaru di atas. Callback format: {@code slikmn_YYYYMM}, contoh {@code slikmn_202605}.</p>
 */
@Component
public class SlikMonthPickerButton {

    private static final String[] LABELS = {
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    };

    private static final int COLS = 3;

    /**
     * Membangun keyboard pilih bulan dengan 12 bulan terakhir.
     *
     * @return keyboard inline siap kirim ke Telegram
     */
    public TdApi.ReplyMarkupInlineKeyboard build() {
        return build(12);
    }

    /**
     * Membangun keyboard pilih bulan.
     *
     * @param monthsToShow jumlah bulan yang ditampilkan (terbaru di atas)
     * @return keyboard inline siap kirim ke Telegram
     */
    public TdApi.ReplyMarkupInlineKeyboard build(int monthsToShow) {
        LocalDate now = LocalDate.now(ZoneOffset.ofHours(7));
        List<TdApi.InlineKeyboardButton> buttons = new ArrayList<>();

        for (int i = 0; i < monthsToShow; i++) {
            LocalDate d = now.minusMonths(i);
            String yyyymm  = String.format("%04d%02d", d.getYear(), d.getMonthValue());
            String label   = LABELS[d.getMonthValue() - 1] + " " + d.getYear();
            buttons.add(tdButton(label, "slikmn_" + yyyymm));
        }

        // Bagi menjadi baris 3 tombol
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += COLS) {
            int end = Math.min(i + COLS, buttons.size());
            rows.add(buttons.subList(i, end).toArray(new TdApi.InlineKeyboardButton[0]));
        }

        return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][]));
    }

    private static TdApi.InlineKeyboardButton tdButton(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback cb = new TdApi.InlineKeyboardButtonTypeCallback();
        cb.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = cb;
        return btn;
    }
}
