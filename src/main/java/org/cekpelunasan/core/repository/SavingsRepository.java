package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Savings;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SavingsRepository extends ReactiveMongoRepository<Savings, String> {

    Flux<Savings> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);
    Mono<Long> countByNameContainingIgnoreCaseAndBranch(String name, String branch);

    Mono<Savings> findByTabId(String tabId);

    Flux<Savings> findByNameContainingIgnoreCase(String name);

    Mono<Savings> findByCif(String cif);
}
