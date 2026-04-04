package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.CustomerHistory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

// NOTE: countCollectStatusByCustomer() removed — implemented in CustomerHistoryService via ReactiveMongoTemplate
@Repository
public interface CustomerHistoryRepository extends ReactiveMongoRepository<CustomerHistory, String> {
}
