package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pembangun keyboard pilihan cabang untuk fitur pencarian tabungan.
 *
 * <p>Ketika user mencari tabungan berdasarkan nama dan nasabah tersebut
 * terdaftar di lebih dari satu cabang, class ini menghasilkan tombol-tombol
 * cabang yang bisa dipilih. Maksimal 4 tombol per baris agar tampilan
 * tetap rapi di layar mobile.
 *
 * <p>Setiap tombol cabang yang ditekan akan memicu callback berawalan
 * {@code "branchtab"}, yang ditangani oleh
 * {@link org.cekpelunasan.platform.telegram.callback.handler.SavingsSelectBranchCallbackHandler}.
 */
@Component
public class SelectSavingsBranch {

    /**
     * Membangun inline keyboard berisi tombol pilihan cabang untuk tabungan.
     *
     * <p>Cabang-cabang dari set diurutkan berdasarkan iterasi set (tidak ada
     * jaminan urutan tertentu) dan disusun maksimal 4 tombol per baris.
     * Baris terakhir mungkin memiliki lebih sedikit dari 4 tombol.
     *
     * @param branchName kumpulan nama cabang yang tersedia untuk nasabah yang dicari
     * @param query      nama nasabah yang disisipkan ke data callback setiap tombol
     * @return objek inline keyboard berisi tombol-tombol pilihan cabang
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicSelectBranch(Set<String> branchName, String query) {
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        List<TdApi.InlineKeyboardButton> currentRow = new ArrayList<>();
        List<String> branchList = new ArrayList<>(branchName);

        for (int i = 0; i < branchList.size(); i++) {
            currentRow.add(tdButton(branchList.get(i), "branchtab_" + branchList.get(i) + "_" + query));
            if (currentRow.size() == 4 || i == branchList.size() - 1) {
                rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }
        return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][]));
    }

    /**
     * Membuat satu tombol inline keyboard dengan teks dan data callback yang diberikan.
     *
     * @param text teks nama cabang yang ditampilkan pada tombol
     * @param data string callback yang dikirim ke bot ketika tombol ini ditekan
     * @return objek {@link TdApi.InlineKeyboardButton} yang siap dipakai
     */
    private static TdApi.InlineKeyboardButton tdButton(String text, String data) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
