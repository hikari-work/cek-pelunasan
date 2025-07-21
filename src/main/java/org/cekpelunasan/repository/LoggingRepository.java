package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Logging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoggingRepository extends JpaRepository<Logging, String> {
	List<Logging> getLoggingById(String id);
}
