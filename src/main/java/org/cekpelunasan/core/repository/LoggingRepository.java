package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Logging;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoggingRepository extends ReactiveMongoRepository<Logging, String> {
}
