package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.DataUpdateLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface DataUpdateLogRepository extends ReactiveMongoRepository<DataUpdateLog, String> {
}
