package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.PaymentDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository untuk mengakses data detail pembayaran angsuran dari koleksi
 * MongoDB {@code payment_details}.
 * <p>
 * Data ini digunakan untuk memantau realisasi pengelolaan outstanding per AO,
 * siapa saja yang sudah melakukan pengurangan outstanding, dan nasabah mana
 * yang terindikasi pelunasan dipercepat.
 * </p>
 */
@Repository
public interface PaymentDetailsRepository extends ReactiveMongoRepository<PaymentDetails, String> {

    /**
     * Mengambil seluruh data angsuran untuk satu AO tertentu, dengan paginasi.
     *
     * @param kodeAo   kode Account Officer
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream data angsuran AO tersebut
     */
    Flux<PaymentDetails> findByKodeAo(String kodeAo, Pageable pageable);

    /**
     * Menghitung total record angsuran untuk satu AO tertentu.
     *
     * @param kodeAo kode Account Officer
     * @return jumlah total record
     */
    Mono<Long> countByKodeAo(String kodeAo);

    /**
     * Mengambil seluruh data angsuran untuk satu cabang pada tanggal tertentu, dengan paginasi.
     *
     * @param kodeCabang kode cabang
     * @param tanggal    tanggal dalam format yyyyMMdd
     * @param pageable   konfigurasi halaman dan ukuran data
     * @return stream data angsuran cabang tersebut
     */
    Flux<PaymentDetails> findByKodeCabangAndTanggal(String kodeCabang, String tanggal, Pageable pageable);

    /**
     * Menghitung total record angsuran untuk satu cabang pada tanggal tertentu.
     *
     * @param kodeCabang kode cabang
     * @param tanggal    tanggal dalam format yyyyMMdd
     * @return jumlah total record
     */
    Mono<Long> countByKodeCabangAndTanggal(String kodeCabang, String tanggal);

    /**
     * Mengambil data pelunasan (flag pelunasan = true) pada tanggal tertentu, dengan paginasi.
     *
     * @param tanggal       tanggal dalam format yyyyMMdd
     * @param flagPelunasan flag pelunasan
     * @param pageable      konfigurasi halaman dan ukuran data
     * @return stream data angsuran pelunasan
     */
    Flux<PaymentDetails> findByTanggalAndFlagPelunasan(String tanggal, boolean flagPelunasan, Pageable pageable);

    /**
     * Menghitung total record pelunasan pada tanggal tertentu.
     *
     * @param tanggal       tanggal dalam format yyyyMMdd
     * @param flagPelunasan flag pelunasan
     * @return jumlah total record
     */
    Mono<Long> countByTanggalAndFlagPelunasan(String tanggal, boolean flagPelunasan);

    /**
     * Mengambil seluruh data angsuran untuk satu AO pada tanggal tertentu.
     *
     * @param kodeAo  kode Account Officer
     * @param tanggal tanggal dalam format yyyyMMdd
     * @return stream semua record angsuran AO tersebut pada tanggal yang diminta
     */
    Flux<PaymentDetails> findByKodeAoAndTanggal(String kodeAo, String tanggal);
}
