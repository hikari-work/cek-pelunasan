package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.KolekTas;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface KolekTasRepository extends ReactiveMongoRepository<KolekTas, String> {
    Flux<KolekTas> findByKelompokIgnoreCase(String kelompok, Pageable pageable);
    Mono<Long> countByKelompokIgnoreCase(String kelompok);
}
