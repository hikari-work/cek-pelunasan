package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Repayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface RepaymentRepository extends JpaRepository<Repayment, Long> {


	Page<Repayment> findByNameContainingIgnoreCase(String name, Pageable pageable);

	Boolean existsByNameIsLikeIgnoreCase(String name);
}
