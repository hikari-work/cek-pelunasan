package org.cekpelunasan.service.credithistory;

import com.opencsv.CSVReader;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.cekpelunasan.entity.CreditHistory;
import org.cekpelunasan.repository.CreditHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class CreditHistoryService {
	private static final Logger log = LoggerFactory.getLogger(CreditHistoryService.class);
	private final CreditHistoryRepository creditHistoryRepository;

	public CreditHistoryService(CreditHistoryRepository creditHistoryRepository) {
		this.creditHistoryRepository = creditHistoryRepository;
	}

	public Page<CreditHistory> searchAddressByKeywords(List<String> keywords, int page) {
		log.info("Searching for address with keywords: {}", keywords);
		Specification<CreditHistory> specification = (root, query, cb) -> {
			query.distinct(true);


			Subquery<String> subquery = query.subquery(String.class);
			Root<CreditHistory> subRoot = subquery.from(CreditHistory.class);
			subquery.select(subRoot.get("customerId"))
				.where(cb.equal(cb.upper(subRoot.get("status")), "A"));
			List<Predicate> predicates = keywords.stream()
				.map(String::trim)
				.filter(word -> !word.isEmpty())
				.map(word -> cb.like(
					cb.upper(root.get("address")),
					"%" + word.toUpperCase() + "%"))
				.toList();

			// Use AND to ensure all keywords are present in the address
			Predicate addressCondition = cb.and(predicates.toArray(new Predicate[0]));
			Predicate notInSubQuery = cb.not(root.get("customerId").in(subquery));

			return cb.and(addressCondition, notInSubQuery);
		};

		Pageable pageable = PageRequest.of(page, 5);
		Page<CreditHistory> results = creditHistoryRepository.findAll(specification, pageable);
		log.info("Query returned {} results", results.getTotalElements());

		return results;
	}

	public void saveAll(List<CreditHistory> creditHistories) {
		creditHistoryRepository.saveAll(creditHistories);
	}

	public void parseCsvAndSaveIt(Path path) {
		creditHistoryRepository.deleteAll();

		final int BATCH_SIZE = 1000;
		final int MAX_CONCURRENT_TASK = 10;
		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASK);
		List<CreditHistory> currenBatch = new ArrayList<>(BATCH_SIZE);
		int counter = 0;
		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			while ((line = reader.readNext()) != null) {
				currenBatch.add(mapToCreditHistory(line));
				if (currenBatch.size() >= BATCH_SIZE) {
					List<CreditHistory> batcToSave = new ArrayList<>(currenBatch);
					semaphore.acquire();
					executorService.submit(() -> {
						try {
							saveAll(batcToSave);
						} catch (Exception e) {
							log.error("Error {}", e.getMessage());
						} finally {
							semaphore.release();
						}
					});
					counter += BATCH_SIZE;
					System.out.println("Submitted Batch " + counter);
					currenBatch.clear();
				}
			}
			if (!currenBatch.isEmpty()) {
				List<CreditHistory> batchToSave = new ArrayList<>(currenBatch);
				semaphore.acquire();
				executorService.submit(() -> {
					try {
						saveAll(batchToSave);
					} catch (Exception e) {
						log.error("Error Save {}", e.getMessage());
					} finally {
						semaphore.release();
					}
				});
				System.out.println("Submitted final batch: " + (counter + currenBatch.size()));

			}
			executorService.shutdown();
			executorService.awaitTermination(1, TimeUnit.HOURS);
		} catch (Exception e) {
			log.error("Gagal");
		}
		System.out.println("Sudah Selesai");
	}

	public CreditHistory mapToCreditHistory(String[] line) {
		return CreditHistory.builder()
			.date(Long.parseLong(line[0]))
			.creditId(line[1])
			.customerId(line[2])
			.name(line[3])
			.status(line[4])
			.address(line[5])
			.phone(line[6])
			.build();
	}
}