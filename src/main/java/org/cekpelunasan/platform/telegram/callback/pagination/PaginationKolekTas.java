package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Pembangun tombol paginasi untuk daftar data kolektas (kolektibilitas tabungan).
 *
 * <p>Class ini membungkus pemanggilan {@link PaginationMarkupBuilder} khusus
 * untuk fitur kolektas yang menampilkan data tabungan berdasarkan kelompok.
 * Tombol menggunakan prefix {@code "koltas"} sehingga callback-nya ditangani oleh
 * {@link org.cekpelunasan.platform.telegram.callback.handler.KolektasCallbackHandler}.
 */
@Component
public class PaginationKolekTas {

    /**
     * Membangun inline keyboard paginasi untuk daftar data kolektas berdasarkan kelompok.
     *
     * @param page        halaman data kolektas hasil query
     * @param currentPage nomor halaman saat ini (0-based)
     * @param query       kode kelompok kolektas, misalnya {@code "kpr.1"}
     * @return objek inline keyboard dengan tombol navigasi Prev/Next yang sesuai
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<?> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "koltas", query);
    }
}
