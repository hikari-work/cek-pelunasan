package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Paying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayingRepository extends JpaRepository<Paying, String> {
}
