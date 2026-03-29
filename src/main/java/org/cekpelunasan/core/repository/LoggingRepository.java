package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Logging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoggingRepository extends JpaRepository<Logging, String> {
}
