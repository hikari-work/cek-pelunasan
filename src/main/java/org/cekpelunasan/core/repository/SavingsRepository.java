package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository untuk mengakses data rekening tabungan nasabah dari koleksi MongoDB {@code savings}.
 * <p>
 * Mendukung berbagai skenario pencarian: cari by nama (partial match) dengan paginasi,
 * cari langsung by nomor rekening (tabId), atau cari by nomor CIF. Semua operasi
 * bersifat reaktif dan non-blocking.
 * </p>
 */
@Repository
public interface SavingsRepository extends ReactiveMongoRepository<Savings, String> {

    /**
     * Mencari rekening tabungan berdasarkan nama pemilik (parsial, tidak peka huruf besar/kecil)
     * dalam satu cabang tertentu, dengan paginasi.
     * <p>
     * Dipakai ketika AO mengetikkan nama nasabah dan bot menampilkan daftar rekening
     * yang cocok halaman per halaman.
     * </p>
     *
     * @param name     sebagian atau seluruh nama pemilik rekening yang dicari
     * @param branch   kode cabang tempat rekening tersebut terdaftar
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream rekening tabungan yang nama pemiliknya cocok dengan kata kunci
     */
    Flux<Savings> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);

    /**
     * Menghitung jumlah rekening tabungan yang nama pemiliknya cocok dengan kata kunci
     * dalam satu cabang. Dipakai untuk menghitung total halaman paginasi.
     *
     * @param name   kata kunci nama yang dicari
     * @param branch kode cabang
     * @return jumlah rekening yang cocok
     */
    Mono<Long> countByNameContainingIgnoreCaseAndBranch(String name, String branch);

    /**
     * Mengambil satu rekening tabungan berdasarkan nomor rekening (tabId) yang tercetak
     * di buku tabungan nasabah. Pencarian ini selalu mengembalikan paling banyak satu hasil
     * karena nomor rekening bersifat unik.
     *
     * @param tabId nomor rekening tabungan
     * @return data rekening tabungan yang bersangkutan, atau kosong jika tidak ditemukan
     */
    Mono<Savings> findByTabId(String tabId);

    /**
     * Mencari rekening tabungan dari semua cabang berdasarkan nama pemilik (parsial,
     * tidak peka huruf besar/kecil). Dipakai untuk pencarian lintas cabang.
     *
     * @param name sebagian atau seluruh nama pemilik rekening yang dicari
     * @return stream semua rekening yang nama pemiliknya mengandung kata kunci tersebut
     */
    Flux<Savings> findByNameContainingIgnoreCase(String name);

    /**
     * Mengambil satu rekening tabungan berdasarkan nomor CIF nasabah.
     * Karena satu CIF bisa punya banyak rekening, method ini mengembalikan
     * rekening pertama yang ditemukan (biasanya rekening tabungan utama).
     *
     * @param cif nomor CIF nasabah
     * @return rekening tabungan yang terkait dengan CIF tersebut, atau kosong jika tidak ada
     */
    Mono<Savings> findByCif(String cif);
}
