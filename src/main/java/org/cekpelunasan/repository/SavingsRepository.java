package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Savings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface SavingsRepository extends JpaRepository<Savings, Long>, JpaSpecificationExecutor<Savings> {

	Page<Savings> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);

	@Query("SELECT DISTINCT b.branch FROM Savings b")
	Set<String> findByBranch();

	Optional<Savings> findByTabId(String tabId);

}
