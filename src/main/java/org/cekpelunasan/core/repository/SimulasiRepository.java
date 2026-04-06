package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Simulasi;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repository untuk mengakses data jadwal angsuran simulasi dari koleksi MongoDB {@code simulasi}.
 * <p>
 * Dipakai terutama oleh fitur simulasi pelunasan: ketika AO meminta simulasi untuk
 * suatu SPK, seluruh baris jadwal angsuran untuk SPK tersebut diambil dari sini,
 * lalu dihitung secara kumulatif oleh service untuk menghasilkan {@code SimulasiResult}.
 * </p>
 */
@Repository
public interface SimulasiRepository extends ReactiveMongoRepository<Simulasi, String> {

    /**
     * Mengambil semua baris jadwal angsuran untuk nomor SPK tertentu.
     * Hasilnya mencakup semua periode angsuran yang belum terbayar, dipakai
     * sebagai bahan kalkulasi simulasi pelunasan.
     *
     * @param spk nomor SPK kredit yang jadwal angsurannya ingin diambil
     * @return stream semua baris jadwal angsuran untuk SPK tersebut
     */
    Flux<Simulasi> findBySpk(String spk);
}
