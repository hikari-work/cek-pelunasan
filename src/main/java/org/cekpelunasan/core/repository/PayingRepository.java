package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Paying;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayingRepository extends ReactiveMongoRepository<Paying, String> {
}
