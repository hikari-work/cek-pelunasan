package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.CreditHistory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

// NOTE: Specification/JpaSpecificationExecutor removed — query implemented in CreditHistoryService via ReactiveMongoTemplate
@Repository
public interface CreditHistoryRepository extends ReactiveMongoRepository<CreditHistory, String> {
    Flux<CreditHistory> findByStatusIgnoreCase(String status);
}
