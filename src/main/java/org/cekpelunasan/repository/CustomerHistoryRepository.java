package org.cekpelunasan.repository;

import org.cekpelunasan.entity.CustomerHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerHistoryRepository extends JpaRepository<CustomerHistory, Long> {

    @Query(value = """
    SELECT
        SUM(CASE WHEN collect_status = '02' THEN 1 ELSE 0 END),
        SUM(CASE WHEN collect_status = '03' THEN 1 ELSE 0 END),
        SUM(CASE WHEN collect_status = '04' THEN 1 ELSE 0 END),
        SUM(CASE WHEN collect_status = '05' THEN 1 ELSE 0 END)
    FROM customer_history
    WHERE customer_id = :customerId
""", nativeQuery = true)
    List<Object[]> countCollectStatusByCustomer(@Param("customerId") String customerId);


}
