package org.cekpelunasan.repository;

import org.cekpelunasan.entity.Bills;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface BillsRepository extends JpaRepository<Bills, String> {

	@Modifying
	@Query(value = "TRUNCATE TABLE tagihan", nativeQuery = true)
	void deleteAllFast();

	@Query("SELECT b FROM Bills b WHERE b.branch = :branch AND b.noSpk NOT IN (SELECT p.id FROM Paying p)")
	List<Bills> findUnpaidBillsByBranch(String branch);

	Page<Bills> findByAccountOfficerAndPayDown(String accountOfficer, String payDown, Pageable pageable);

	Page<Bills> findByBranchAndPayDownOrderByAccountOfficer(String branch, String payDown, Pageable pageable);

	Page<Bills> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);

	@Query("""
       SELECT b FROM Bills b
       WHERE b.officeLocation = :branch
       AND (b.minInterest + b.minPrincipal) > 0
       """)
	Page<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndBranch(Long minInterest, Long minPrincipal, String branch, Pageable pageable);

	@Query("""
    SELECT b FROM Bills b
    WHERE b.branch = '1075'
    AND b.kios = :kios
    AND (b.minInterest + b.minPrincipal) > 0
    """)
	Page<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndKios(Long minInterest, Long minimalPrincipal, String kios, Pageable pageable);

	// Method baru: Filter by branch dan kios
	@Query("""
    SELECT b FROM Bills b
    WHERE b.branch = :branch
    AND b.kios = :kios
    AND (COALESCE(b.minInterest, 0) + COALESCE(b.minPrincipal, 0)) > :minTotal
    """)
	Page<Bills> findByBranchAndKiosAndTotalMin(@Param("branch") String branch,
											   @Param("kios") String kios,
											   @Param("minTotal") Long minTotal,
											   Pageable pageable);

	// Method baru: Filter by branch saja (untuk semua kios)
	@Query("""
    SELECT b FROM Bills b
    WHERE b.branch = :branch
    AND (COALESCE(b.minInterest, 0) + COALESCE(b.minPrincipal, 0)) > :minTotal
    """)
	Page<Bills> findByBranchAndTotalMin(@Param("branch") String branch,
										@Param("minTotal") Long minTotal,
										Pageable pageable);

	// Hapus method lama ini (tidak dipakai lagi)
	@Query("""
    SELECT b FROM Bills b
    WHERE b.branch = '1075'
    AND b.kios = :kios
    AND (COALESCE(b.minInterest, 0) + COALESCE(b.minPrincipal, 0)) > :minTotal
    """)
	Page<Bills> findByKiosAndTotalMin(@Param("kios") String kios,
									  @Param("minTotal") Long minTotal,
									  Pageable pageable);

	@Query("""
       SELECT b FROM Bills b
       WHERE b.accountOfficer = :accountOfficer
       AND (b.minInterest + b.minPrincipal) > 0
       """)
	Page<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(Long minInterest, Long minPrincipal, String accountOfficer, Pageable pageable);

	@Query("SELECT DISTINCT b.branch FROM Bills b")
	Set<String> findDistinctBranchByBranch();

	@Query("SELECT DISTINCT b.accountOfficer FROM Bills b")
	Set<String> findDistinctByAccountOfficer();

	// Method baru: Filter due date by branch
	@Query("SELECT b FROM Bills b WHERE b.branch = :branch AND b.dueDate LIKE CONCAT(:dueDate, '%')")
	List<Bills> findByBranchAndDueDateContaining(@Param("branch") String branch, @Param("dueDate") String dueDate);

	// Method baru: Filter realization by branch
	@Query("SELECT b FROM Bills b WHERE b.branch = :branch AND LOWER(b.realization) LIKE CONCAT(LOWER(:realization), '%')")
	List<Bills> findByBranchAndRealizationContaining(@Param("branch") String branch, @Param("realization") String realization);

	List<Bills> findByDueDateContaining(String dueDate);

	List<Bills> findByRealizationIsContainingIgnoreCase(String branch);

	List<Bills> findAllByBranch(String branch);
}