package org.cekpelunasan.repository;

import org.cekpelunasan.entity.CreditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {


    @Query("""
        SELECT DISTINCT ch FROM credit_history ch
        WHERE ch.address LIKE %:address%
        AND ch.customerId NOT IN (
            SELECT ch2.customerId
            FROM credit_history ch2
            WHERE ch2.status = 'A'
        )
""")
    Page<CreditHistory> findByAddressLikeIgnoreCase(@Param("address") String address, Pageable pageable);
}
