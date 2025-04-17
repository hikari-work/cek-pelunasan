package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Repayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepaymentRepository extends JpaRepository<Repayment, Long> {


    Page<Repayment> findByNameIsLike(String name, Pageable pageable);

    Page<Repayment> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
