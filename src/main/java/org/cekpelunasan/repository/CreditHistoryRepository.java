package org.cekpelunasan.repository;

import org.cekpelunasan.entity.CreditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long>, JpaSpecificationExecutor<CreditHistory> {

}
