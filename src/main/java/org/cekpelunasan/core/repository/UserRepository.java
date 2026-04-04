package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface UserRepository extends ReactiveMongoRepository<User, Long> {
    Flux<User> findByUserCode(String userCode);
}
