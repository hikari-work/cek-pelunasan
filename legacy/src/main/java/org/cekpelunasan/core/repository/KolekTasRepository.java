package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.KolekTas;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository untuk mengakses data daftar kunjungan penagihan (Kolek Tas)
 * dari koleksi MongoDB {@code kolek_tas}.
 * <p>
 * Fitur utama yang didukung adalah pencarian berdasarkan kelompok binaan nasabah.
 * AO bisa menampilkan daftar nasabah dalam kelompoknya yang perlu dikunjungi,
 * lengkap dengan paginasi agar tidak banjir data sekaligus di chat bot.
 * </p>
 */
@Repository
public interface KolekTasRepository extends ReactiveMongoRepository<KolekTas, String> {

    /**
     * Mengambil daftar nasabah dalam suatu kelompok binaan, tidak peka huruf besar/kecil,
     * dengan dukungan paginasi.
     * <p>
     * Cocok dipakai ketika AO mengetikkan nama kelompoknya dan bot menampilkan
     * nasabah satu halaman per satu halaman.
     * </p>
     *
     * @param kelompok nama kelompok binaan yang dicari
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream data nasabah dalam kelompok tersebut
     */
    Flux<KolekTas> findByKelompokIgnoreCase(String kelompok, Pageable pageable);

    /**
     * Mengambil seluruh nasabah dalam suatu kelompok binaan tanpa paginasi.
     * Dipakai oleh Mini App yang menampilkan semua anggota kelompok sekaligus.
     *
     * @param kelompok nama kelompok binaan yang dicari
     * @return stream semua nasabah dalam kelompok tersebut
     */
    Flux<KolekTas> findByKelompokIgnoreCase(String kelompok);

    /**
     * Menghitung total nasabah dalam suatu kelompok binaan.
     * Dipakai untuk menghitung berapa halaman yang diperlukan dalam paginasi.
     *
     * @param kelompok nama kelompok binaan yang dihitung
     * @return jumlah total nasabah dalam kelompok tersebut
     */
    Mono<Long> countByKelompokIgnoreCase(String kelompok);
}
