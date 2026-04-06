package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface BillsRepository extends ReactiveMongoRepository<Bills, String> {


    Flux<Bills> findByAccountOfficerAndPayDown(String accountOfficer, String payDown, Pageable pageable);
    Mono<Long> countByAccountOfficerAndPayDown(String accountOfficer, String payDown);

    Flux<Bills> findByBranchAndPayDownOrderByAccountOfficer(String branch, String payDown, Pageable pageable);
    Mono<Long> countByBranchAndPayDown(String branch, String payDown);

    Flux<Bills> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);
    Mono<Long> countByNameContainingIgnoreCaseAndBranch(String name, String branch);

    @Query("{ 'officeLocation': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Flux<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndBranch(Long minInterest, Long minPrincipal, String branch, Pageable pageable);

    @Query("{ 'officeLocation': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Mono<Long> countByMinInterestOrMinPrincipalIsGreaterThanAndBranch(Long minInterest, Long minPrincipal, String branch);

    @Query("{ 'branch': '1075', 'kios': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Flux<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndKios(Long minInterest, Long minPrincipal, String kios, Pageable pageable);

    @Query("{ 'branch': ?0, 'kios': ?1, '$expr': { '$gt': [{ '$add': [{ '$ifNull': ['$minInterest', 0] }, { '$ifNull': ['$minPrincipal', 0] }] }, ?2] } }")
    Flux<Bills> findByBranchAndKiosAndTotalMin(String branch, String kios, Long minTotal, Pageable pageable);

    @Query("{ 'branch': ?0, '$expr': { '$gt': [{ '$add': [{ '$ifNull': ['$minInterest', 0] }, { '$ifNull': ['$minPrincipal', 0] }] }, ?1] } }")
    Flux<Bills> findByBranchAndTotalMin(String branch, Long minTotal, Pageable pageable);

    @Query("{ 'branch': '1075', 'kios': ?0, '$expr': { '$gt': [{ '$add': [{ '$ifNull': ['$minInterest', 0] }, { '$ifNull': ['$minPrincipal', 0] }] }, ?1] } }")
    Flux<Bills> findByKiosAndTotalMin(String kios, Long minTotal, Pageable pageable);

    @Query("{ 'accountOfficer': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Flux<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(Long minInterest, Long minPrincipal, String accountOfficer, Pageable pageable);

    @Query("{ 'accountOfficer': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Mono<Long> countByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(Long minInterest, Long minPrincipal, String accountOfficer);

    @Query("{ 'branch': ?0, 'dueDate': { '$regex': '^?1' } }")
    Flux<Bills> findByBranchAndDueDateContaining(String branch, String dueDate);

    @Query("{ 'branch': ?0, 'realization': { '$regex': '^?1', '$options': 'i' } }")
    Flux<Bills> findByBranchAndRealizationContaining(String branch, String realization);

    Flux<Bills> findByDueDateContaining(String dueDate);

    Flux<Bills> findByRealizationIsContainingIgnoreCase(String realization);

    Flux<Bills> findAllByBranch(String branch);

    // For findByBranchAndNoSpkNotIn (used to implement findUnpaidBillsByBranch in service)
    Flux<Bills> findByBranchAndNoSpkNotIn(String branch, Iterable<String> noSpkList);
}
