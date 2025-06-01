package org.cekpelunasan.service.savings;

import com.opencsv.CSVReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.repository.SavingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class SavingsService {

	private static final Logger log = LoggerFactory.getLogger(SavingsService.class);
	private final SavingsRepository savingsRepository;
	private final EntityManager em;

	public SavingsService(SavingsRepository savingsRepository, EntityManager em) {
		this.em = em;
		this.savingsRepository = savingsRepository;
	}

	public void batchSavingAccounts(List<Savings> savingsList) {
		savingsRepository.saveAll(savingsList);
	}

	public Page<Savings> findByNameAndBranch(String name, String branch, int page) {
		Pageable pageable = PageRequest.of(page, 5);
		return savingsRepository.findByNameContainingIgnoreCaseAndBranch(name, branch, pageable);
	}

	public void parseCsvAndSaveIntoDatabase(Path path) {
		savingsRepository.deleteAll();
		final int BATCH_SIZE = 1000;
		final int MAX_CONCURRENT_TASK = Runtime.getRuntime().availableProcessors() * 2;

		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASK);
		List<Savings> currentBatch = new ArrayList<>(BATCH_SIZE);
		int counter = 0;

		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			while ((line = reader.readNext()) != null) {
				currentBatch.add(mapToSavings(line));
				if (currentBatch.size() >= BATCH_SIZE) {
					processBatch(currentBatch, executor, semaphore);
					counter += BATCH_SIZE;
					log.info("Submitted Batch {}", counter);
					currentBatch.clear();
				}
			}

			if (!currentBatch.isEmpty()) {
				processBatch(currentBatch, executor, semaphore);
				log.info("Submitted final batch: {}", counter + currentBatch.size());
			}

			executor.shutdown();
			log.info("CSV file parsed and data saved to database successfully.");
		} catch (Exception e) {
			log.error("Error parsing CSV file: {}", e.getMessage(), e);
		}
	}

	private void processBatch(List<Savings> batch, ExecutorService executor, Semaphore semaphore) throws InterruptedException {
		List<Savings> batchToSave = new ArrayList<>(batch);
		semaphore.acquire();
		executor.submit(() -> {
			try {
				batchSavingAccounts(batchToSave);
			} catch (Exception e) {
				log.error("Error saving batch: {}", e.getMessage(), e);
			} finally {
				semaphore.release();
			}
		});
	}

	public Savings mapToSavings(String[] line) {
		return Savings.builder()
			.branch(line[0])
			.type(line[1])
			.cif(line[2])
			.tabId(line[3])
			.name(line[4])
			.address(line[5])
			.balance(parseBigDecimal(line[6]))
			.transaction(parseBigDecimal(line[7]))
			.accountOfficer(line[8])
			.phone(line[9])
			.minimumBalance(parseBigDecimal(line[10]))
			.blockingBalance(parseBigDecimal(line[11]))
			.build();
	}

	private BigDecimal parseBigDecimal(String value) {
		return BigDecimal.valueOf(Long.parseLong(value));
	}

	public Set<String> listAllBranch(String name) {
		System.out.println(name);
		return savingsRepository.findAllByNameContainingIgnoreCase(name).stream()
			.sorted()
			.peek(System.out::println)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Optional<Savings> findById(String id) {
		log.info("Searching for account with ID: {}", id);
		return savingsRepository.findByTabId(id);
	}


	@Transactional
	public Page<Savings> findFilteredSavings(List<String> addressKeywords, Pageable pageable) {
		log.info("Starting findFilteredSavings with keywords: {} and page: {}", addressKeywords, pageable);
		try {
			CompletableFuture<List<Savings>> resultsFuture = CompletableFuture.supplyAsync(() -> executeUniqueRecordsQuery(addressKeywords, pageable));
			CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(() -> executeCountUniqueQuery(addressKeywords));
			CompletableFuture.allOf(resultsFuture, countFuture).join();

			List<Savings> resultList = resultsFuture.get();
			Long total = countFuture.get();

			log.info("Query completed successfully. Found {} unique CIFs matching keywords.", total);
			return new PageImpl<>(resultList, pageable, total);
		} catch (Exception e) {
			log.error("Error in findFilteredSavings: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to execute filtered savings query: " + e.getMessage(), e);
		}
	}

	private List<Savings> executeUniqueRecordsQuery(List<String> addressKeywords, Pageable pageable) {
		long startTime = System.currentTimeMillis();
		try {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			CriteriaQuery<Savings> query = cb.createQuery(Savings.class);
			Root<Savings> root = query.from(Savings.class);

			Subquery<Long> minIdSubquery = buildMinIdSubquery(query, cb, addressKeywords);

			query.select(root).where(root.get("id").in(minIdSubquery));
			TypedQuery<Savings> typedQuery = em.createQuery(query);
			typedQuery.setFirstResult((int) pageable.getOffset());
			typedQuery.setMaxResults(pageable.getPageSize());

			List<Savings> result = typedQuery.getResultList();
			log.debug("Executed record query in {} ms", System.currentTimeMillis() - startTime);
			return result;
		} catch (Exception e) {
			log.error("Error executing unique records query: {}", e.getMessage(), e);
			throw e;
		}
	}

	private Long executeCountUniqueQuery(List<String> addressKeywords) {
		long startTime = System.currentTimeMillis();
		try {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
			Root<Savings> countRoot = countQuery.from(Savings.class);

			countQuery.select(cb.countDistinct(countRoot.get("cif")));

			Predicate addressPredicate = buildAddressPredicates(cb, countRoot, addressKeywords);
			Predicate notInBills = buildNotInBillsPredicate(cb, countRoot, countQuery);
			countQuery.where(cb.and(addressPredicate, notInBills));

			Long result = em.createQuery(countQuery).getSingleResult();
			log.debug("Executed count query in {} ms", System.currentTimeMillis() - startTime);
			return result;
		} catch (Exception e) {
			log.error("Error executing count query: {}", e.getMessage(), e);
			throw e;
		}
	}

	private Predicate buildNotInBillsPredicate(CriteriaBuilder cb, Root<Savings> root, CriteriaQuery<?> query) {
		Subquery<String> billsSubquery = query.subquery(String.class);
		Root<Bills> billsRoot = billsSubquery.from(Bills.class);
		billsSubquery.select(billsRoot.get("customerId"))
			.where(cb.isNotNull(billsRoot.get("customerId")));
		return cb.not(root.get("cif").in(billsSubquery));
	}

	private Subquery<Long> buildMinIdSubquery(CriteriaQuery<?> query, CriteriaBuilder cb, List<String> addressKeywords) {
		Subquery<Long> minIdSubquery = query.subquery(Long.class);
		Root<Savings> subRoot = minIdSubquery.from(Savings.class);

		Predicate addressPredicate = buildAddressPredicates(cb, subRoot, addressKeywords);
		Predicate notInBills = buildNotInBillsPredicate(cb, subRoot, query);

		minIdSubquery.select(cb.min(subRoot.get("id")))
			.where(cb.and(addressPredicate, notInBills))
			.groupBy(subRoot.get("cif"));

		return minIdSubquery;
	}

	private Predicate buildAddressPredicates(CriteriaBuilder cb, Root<Savings> root, List<String> addressKeywords) {
		if (addressKeywords == null || addressKeywords.isEmpty()) {
			return cb.conjunction();
		}

		List<String> processedKeywords = addressKeywords.stream()
			.filter(k -> k != null && !k.trim().isEmpty())
			.map(String::trim)
			.map(String::toLowerCase)
			.toList();

		if (processedKeywords.isEmpty()) {
			return cb.conjunction();
		}

		List<Predicate> likePredicates = processedKeywords.stream()
			.map(keyword -> {
				log.debug("Adding LIKE predicate for keyword: '{}'", keyword);
				return cb.like(cb.lower(root.get("address")), "%" + keyword + "%");
			})
			.toList();

		return cb.and(likePredicates.toArray(new Predicate[0]));
	}
}
