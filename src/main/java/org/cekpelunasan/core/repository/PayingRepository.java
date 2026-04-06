package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Paying;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository untuk mengakses data status pelunasan tagihan dari koleksi MongoDB {@code paying}.
 * <p>
 * Repository ini hanya menggunakan operasi bawaan dari {@link ReactiveMongoRepository}
 * seperti {@code findById}, {@code save}, dan {@code deleteById}. Tidak ada query
 * kustom yang dibutuhkan karena pencarian selalu berdasarkan ID (nomor SPK) secara langsung.
 * </p>
 * <p>
 * Contoh penggunaan: cek apakah SPK tertentu sudah lunas dengan {@code findById(noSpk)},
 * atau tandai lunas dengan {@code save(new Paying(noSpk, true))}.
 * </p>
 */
@Repository
public interface PayingRepository extends ReactiveMongoRepository<Paying, String> {
}
