package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Pembangun tombol paginasi untuk daftar tabungan nasabah berdasarkan nama dan cabang.
 *
 * <p>Class ini membungkus pemanggilan {@link PaginationMarkupBuilder#buildWithBranch}
 * khusus untuk navigasi daftar tabungan. Karena hasil pencarian tabungan
 * membutuhkan dua parameter (nama dan cabang) untuk mempertahankan konteks
 * pada setiap perpindahan halaman, digunakan varian {@code buildWithBranch}.
 *
 * <p>Tombol yang dihasilkan menggunakan prefix {@code "tab"} sehingga
 * callback-nya ditangani oleh
 * {@link org.cekpelunasan.platform.telegram.callback.handler.SavingNextButtonCallbackHandler}.
 */
@Component
public class PaginationSavingsButton {

    /**
     * Membangun inline keyboard paginasi untuk daftar tabungan nasabah.
     *
     * @param page        halaman data tabungan hasil query
     * @param branch      kode cabang yang ikut disertakan dalam data callback tombol
     * @param currentPage nomor halaman saat ini (0-based)
     * @param query       nama nasabah yang digunakan sebagai parameter pencarian
     * @return objek inline keyboard dengan tombol navigasi Prev/Next yang sesuai
     */
    public TdApi.ReplyMarkupInlineKeyboard keyboardMarkup(Page<Savings> page, String branch, int currentPage, String query) {
        return PaginationMarkupBuilder.buildWithBranch(page, currentPage, "tab", query, branch);
    }
}
