package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.MinBungaSession;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MinBungaSessionRepository extends ReactiveMongoRepository<MinBungaSession, String> {
}
