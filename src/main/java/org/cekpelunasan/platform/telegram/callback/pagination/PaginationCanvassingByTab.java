package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Pembangun tombol paginasi untuk fitur canvasing tabungan berdasarkan tab/filter.
 *
 * <p>Class ini membungkus pemanggilan {@link PaginationMarkupBuilder} khusus
 * untuk fitur canvasing yang menampilkan data tabungan yang difilter berdasarkan
 * kata kunci alamat dari tab tertentu. Tombol menggunakan prefix {@code "canvas"}
 * sehingga callback-nya ditangani oleh
 * {@link org.cekpelunasan.platform.telegram.callback.handler.CanvasingTabCallbackHandler}.
 */
@Component
public class PaginationCanvassingByTab {

    /**
     * Membangun inline keyboard paginasi untuk daftar tabungan hasil filter canvasing.
     *
     * @param page        halaman data tabungan hasil filter
     * @param currentPage nomor halaman saat ini (0-based)
     * @param query       kata kunci yang digunakan sebagai filter (bisa berisi spasi atau underscore)
     * @return objek inline keyboard dengan tombol navigasi Prev/Next yang sesuai
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<?> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "canvas", query);
    }
}
