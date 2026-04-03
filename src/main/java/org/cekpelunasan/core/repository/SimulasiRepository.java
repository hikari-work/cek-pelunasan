package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Simulasi;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SimulasiRepository extends ReactiveMongoRepository<Simulasi, String> {
    Flux<Simulasi> findBySpk(String spk);
}
