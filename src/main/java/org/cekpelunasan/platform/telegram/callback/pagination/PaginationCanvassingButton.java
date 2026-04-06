package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Pembangun tombol paginasi untuk fitur canvasing berdasarkan alamat.
 *
 * <p>Class ini membungkus pemanggilan {@link PaginationMarkupBuilder} khusus
 * untuk fitur canvasing yang mencari riwayat kredit berdasarkan kata kunci alamat.
 * Tombol yang dihasilkan menggunakan prefix {@code "canvasing"} sehingga
 * callback-nya ditangani oleh
 * {@link PaginationToCanvasing}.
 */
@Component
public class PaginationCanvassingButton {

    /**
     * Membangun inline keyboard paginasi untuk daftar hasil canvasing berdasarkan alamat.
     *
     * @param page        halaman data hasil canvasing
     * @param currentPage nomor halaman saat ini (0-based)
     * @param query       kata kunci alamat yang digunakan sebagai parameter pencarian
     * @return objek inline keyboard dengan tombol navigasi Prev/Next yang sesuai
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<?> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "canvasing", query);
    }
}
