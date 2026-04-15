package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.SlikNotifiedFile;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Repository reaktif untuk mengakses koleksi {@code slik_notified_files}.
 */
public interface SlikNotifiedFileRepository extends ReactiveMongoRepository<SlikNotifiedFile, String> {

    /**
     * Mengecek apakah file dengan key tertentu sudah pernah dinotifikasi.
     *
     * @param fileKey key/nama file di bucket R2
     * @return {@link Mono} berisi {@code true} jika sudah ada, {@code false} jika belum
     */
    Mono<Boolean> existsByFileKey(String fileKey);
}
