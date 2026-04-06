package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repository untuk mengakses data pengguna bot dari koleksi MongoDB {@code users}.
 * <p>
 * Primary key yang dipakai adalah chat ID Telegram (Long), sehingga pencarian pengguna
 * berdasarkan chat ID bisa dilakukan langsung lewat {@code findById}. Repository ini
 * juga menyediakan pencarian berdasarkan kode AO untuk keperluan manajemen pengguna
 * oleh admin.
 * </p>
 */
@Repository
public interface UserRepository extends ReactiveMongoRepository<User, Long> {

    /**
     * Mencari pengguna berdasarkan kode AO yang terdaftar.
     * <p>
     * Dipakai misalnya ketika admin ingin memeriksa apakah suatu kode AO sudah terdaftar
     * di sistem, atau untuk mengetahui chat ID dari AO tertentu.
     * Satu kode AO seharusnya hanya terdaftar sekali, tapi method ini mengembalikan
     * Flux untuk mengakomodasi kemungkinan data duplikat.
     * </p>
     *
     * @param userCode kode AO yang ingin dicari
     * @return stream pengguna yang kode AO-nya cocok (biasanya 0 atau 1 hasil)
     */
    Flux<User> findByUserCode(String userCode);
}
