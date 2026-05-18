package org.cekpelunasan.utils.button;

import it.tdlight.jni.TdApi;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Membuat keyboard inline untuk menampilkan daftar nasabah dengan navigasi halaman (pagination).
 * <p>
 * Ketika hasil pencarian tagihan memiliki banyak nasabah, class ini menyusun tombol-tombol
 * berisi nama nasabah (2 per baris) beserta tombol navigasi Prev/Next di bagian atas.
 * Setiap tombol nasabah menyimpan data callback yang berisi nomor SPK untuk membuka detail.
 * </p>
 */
@Component
public class ButtonListForBills {

    /**
     * Membuat keyboard inline lengkap dengan navigasi halaman dan tombol nama nasabah.
     * <p>
     * Baris pertama berisi tombol navigasi (Prev, info halaman, Next).
     * Baris-baris berikutnya berisi nama nasabah, dua per baris.
     * </p>
     *
     * @param names       halaman data tagihan yang akan ditampilkan
     * @param currentPage nomor halaman saat ini (0-indexed)
     * @param query       kata kunci pencarian yang dipakai, disimpan di data callback
     * @param branch      kode cabang yang dicari, disimpan di data callback
     * @return keyboard inline yang siap dipasang ke pesan Telegram
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<Bills> names, int currentPage, String query, String branch) {
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();

        rows.add(buildPaginationRow(names, currentPage, query, branch));
        rows.addAll(buildDataButtons(names.getContent(), currentPage, query, branch));

        return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][]));
    }

    private TdApi.InlineKeyboardButton[] buildPaginationRow(Page<Bills> page, int currentPage, String query, String branch) {
        List<TdApi.InlineKeyboardButton> row = new ArrayList<>();

        int totalElements = (int) page.getTotalElements();
        int currentElement = currentPage * page.getSize() + 1;
        int maxElement = currentPage * page.getSize() + page.getNumberOfElements();

        if (page.hasPrevious()) {
            row.add(tdButton("⬅ Prev", "paging_" + query + "_" + branch + "_" + (currentPage - 1)));
        }

        row.add(tdButton(currentElement + " - " + maxElement + " / " + totalElements, "noop"));

        if (page.hasNext()) {
            row.add(tdButton("Next ➡", "paging_" + query + "_" + branch + "_" + (currentPage + 1)));
        }

        return row.toArray(new TdApi.InlineKeyboardButton[0]);
    }

    private List<TdApi.InlineKeyboardButton[]> buildDataButtons(List<Bills> dataList, int currentPage, String query, String branch) {
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        List<TdApi.InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            Bills bill = dataList.get(i);
            currentRow.add(tdButton(bill.getName(), "tagihan_" + bill.getNoSpk() + "_" + query + "_" + branch + "_" + currentPage));

            if (currentRow.size() == 2 || i == dataList.size() - 1) {
                rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }

        return rows;
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
