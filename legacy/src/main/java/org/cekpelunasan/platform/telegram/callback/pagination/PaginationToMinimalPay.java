package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Pembangun tombol paginasi untuk daftar tagihan minimal bayar.
 *
 * <p>Class ini membungkus pemanggilan {@link PaginationMarkupBuilder} khusus
 * untuk fitur minimal bayar. Tombol yang dihasilkan menggunakan prefix
 * {@code "minimal"} sehingga callback-nya ditangani oleh
 * {@link org.cekpelunasan.platform.telegram.callback.handler.MinimalPayCallbackHandler}.
 */
@Component
public class PaginationToMinimalPay {

    /**
     * Membangun inline keyboard paginasi untuk daftar tagihan minimal bayar.
     *
     * @param page        halaman data tagihan hasil query
     * @param currentPage nomor halaman saat ini (0-based)
     * @param query       kode user (AO atau cabang) yang digunakan sebagai parameter pencarian
     * @return objek inline keyboard dengan tombol navigasi Prev/Next yang sesuai
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<Bills> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "minimal", query);
    }
}
