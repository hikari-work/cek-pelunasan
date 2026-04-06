package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Membuat keyboard inline untuk memilih cabang ketika hasil pencarian nasabah ada di banyak cabang.
 * <p>
 * Ketika pengguna mencari tagihan berdasarkan nama nasabah dan ada kecocokan di beberapa cabang,
 * bot menampilkan tombol pilihan cabang supaya pengguna bisa mempersempit pencarian.
 * Tombol-tombol disusun 3 per baris untuk tampilan yang kompak.
 * </p>
 */
@Component
public class ButtonListForSelectBranch {

    /**
     * Membuat keyboard inline berisi tombol untuk setiap nama cabang yang tersedia.
     * Tombol disusun 3 per baris, dan setiap tombol menyimpan data callback "branch_[cabang]_[query]".
     *
     * @param branchName set nama-nama cabang yang akan dijadikan tombol pilihan
     * @param query      kata kunci pencarian yang tetap dibawa saat pengguna memilih cabang
     * @return keyboard inline yang siap dipasang ke pesan Telegram
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicSelectBranch(Set<String> branchName, String query) {
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        List<TdApi.InlineKeyboardButton> currentRow = new ArrayList<>();
        List<String> branchList = new ArrayList<>(branchName);

        for (int i = 0; i < branchList.size(); i++) {
            currentRow.add(tdButton(branchList.get(i), "branch_" + branchList.get(i) + "_" + query));
            if (currentRow.size() == 3 || i == branchList.size() - 1) {
                rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
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
