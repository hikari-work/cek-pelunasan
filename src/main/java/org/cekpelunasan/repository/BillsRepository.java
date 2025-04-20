package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;


@Repository
public interface BillsRepository extends JpaRepository<Bills, String> {

    Optional<Bills> findByBranch(String branch);

    Page<Bills> findByAccountOfficerAndDueDateLike(String accountOfficer, String dueDate, Pageable pageable);

    Page<Bills> findByBranchAndDueDateLike(String branch, String dueDate, Pageable pageable);

    Page<Bills> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);

    Page<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndBranch(Long minInterest, Long minPrincipal, String branch, Pageable pageable);

    Page<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(Long minInterest, Long minPrincipal, String accountOfficer, Pageable pageable);


    @Query("SELECT DISTINCT b.branch FROM Bills b")
    Set<String> findDistinctBranchByBrach();


}
