package org.cekpelunasan.platform.telegram.callback.pagination;

import it.tdlight.jni.TdApi;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Pembangun tombol paginasi untuk daftar tagihan berdasarkan nama/kode AO.
 *
 * <p>Class ini membungkus pemanggilan {@link PaginationMarkupBuilder} khusus
 * untuk fitur pencarian tagihan per nama atau kode AO. Tombol yang dihasilkan
 * menggunakan prefix {@code "pagebills"} sehingga callback-nya ditangani oleh
 * {@link org.cekpelunasan.platform.telegram.callback.handler.BillsByNameCalculatorCallbackHandler}.
 */
@Component
public class PaginationBillsByNameCallbackHandler {

    /**
     * Membangun inline keyboard paginasi untuk daftar tagihan berdasarkan nama.
     *
     * <p>Tombol Prev dan Next hanya muncul jika halaman sebelumnya/berikutnya
     * memang ada. Tombol tengah menampilkan posisi data yang sedang dilihat
     * (misalnya "1 - 5 / 20") dan tidak melakukan aksi apapun jika ditekan.
     *
     * @param page        halaman data tagihan hasil query
     * @param currentPage nomor halaman saat ini (0-based)
     * @param query       kode AO atau cabang yang digunakan sebagai parameter pencarian
     * @return objek inline keyboard yang siap dipasang pada pesan Telegram
     */
    public TdApi.ReplyMarkupInlineKeyboard dynamicButtonName(Page<Bills> page, int currentPage, String query) {
        return PaginationMarkupBuilder.build(page, currentPage, "pagebills", query);
    }
}
