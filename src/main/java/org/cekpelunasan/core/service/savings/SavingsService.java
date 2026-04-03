package org.cekpelunasan.core.service.savings;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.repository.SavingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavingsService {

	private static final Logger log = LoggerFactory.getLogger(SavingsService.class);
	private final SavingsRepository savingsRepository;
	private final ReactiveMongoTemplate mongoTemplate;

	public Mono<Void> batchSavingAccounts(@NonNull List<Savings> savingsList) {
		return savingsRepository.saveAll(savingsList).then();
	}

	public Mono<Page<Savings>> findByNameAndBranch(String name, String branch, int page) {
		Pageable pageable = PageRequest.of(page, 5);
		Mono<List<Savings>> content = savingsRepository
			.findByNameContainingIgnoreCaseAndBranch(name, branch, pageable).collectList();
		Mono<Long> total = savingsRepository
			.countByNameContainingIgnoreCaseAndBranch(name, branch).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
	}

	public Mono<Savings> findByCif(String cif) {
		return savingsRepository.findByCif(cif);
	}

	public Mono<Void> parseCsvAndSaveIntoDatabase(Path path) {
		return savingsRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						String[] line;
						while ((line = reader.readNext()) != null) {
							sink.next(line);
						}
						sink.complete();
					} catch (Exception e) {
						log.error("Error parsing CSV file: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToSavings)
				.buffer(500)
				.flatMap(batch -> batchSavingAccounts(batch)
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc())
			.doOnSuccess(v -> log.info("CSV savings selesai disimpan."));
	}

	public Mono<Void> deleteAll() {
		return savingsRepository.deleteAll();
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

	public Mono<Set<String>> listAllBranch(String name) {
		Criteria criteria = Criteria.where("name").regex(name, "i");
		org.springframework.data.mongodb.core.query.Query query =
			new org.springframework.data.mongodb.core.query.Query(criteria);
		return mongoTemplate.findDistinct(query, "branch", Savings.class, String.class)
			.filter(b -> b != null && !b.isBlank())
			.sort()
			.collectList()
			.map(LinkedHashSet::new);
	}

	public Mono<Savings> findById(String id) {
		log.info("Searching for account with ID: {}", id);
		return savingsRepository.findByTabId(id);
	}

	public Mono<Page<Savings>> findFilteredSavings(List<String> addressKeywords, @NonNull Pageable pageable) {
		log.info("Starting findFilteredSavings with keywords: {} and page: {}", addressKeywords, pageable);

		Mono<List<String>> billsCifsMono = mongoTemplate
			.findDistinct("customerId", Bills.class, String.class)
			.filter(cif -> cif != null && !cif.isBlank())
			.collectList();

		return billsCifsMono.flatMap(billsCifs -> {
			Criteria addressCriteria = buildAddressCriteria(addressKeywords);
			Criteria notInBills = Criteria.where("cif").nin(billsCifs);
			Criteria combined = new Criteria().andOperator(addressCriteria, notInBills);

			AggregationOperation match = Aggregation.match(combined);
			AggregationOperation group = Aggregation.group("cif").first("$$ROOT").as("doc");
			AggregationOperation replaceRoot = Aggregation.replaceRoot("doc");
			AggregationOperation skip = Aggregation.skip(pageable.getOffset());
			AggregationOperation limit = Aggregation.limit(pageable.getPageSize());

			Mono<List<Savings>> resultsMono = mongoTemplate.aggregate(
				Aggregation.newAggregation(match, group, replaceRoot, skip, limit),
				Savings.class, Savings.class
			).collectList();

			AggregationOperation countGroup = Aggregation.group("cif");
			AggregationOperation countAll = Aggregation.count().as("total");
			Mono<Long> countMono = mongoTemplate.aggregate(
				Aggregation.newAggregation(match, countGroup, countAll),
				Savings.class, org.bson.Document.class
			).collectList()
				.map(results -> {
					if (results == null || results.isEmpty()) return 0L;
					return ((Number) results.get(0).get("total")).longValue();
				});

			return Mono.zip(resultsMono, countMono)
				.map(t -> {
					log.info("Query completed successfully. Found {} unique CIFs matching keywords.", t.getT2());
					return (Page<Savings>) new PageImpl<>(t.getT1(), pageable, t.getT2());
				});
		});
	}

	private Criteria buildAddressCriteria(List<String> addressKeywords) {
		if (addressKeywords == null || addressKeywords.isEmpty()) {
			return new Criteria();
		}
		List<Criteria> predicates = addressKeywords.stream()
			.filter(k -> k != null && !k.trim().isEmpty())
			.map(k -> Criteria.where("address").regex(k.trim(), "i"))
			.collect(Collectors.toList());
		if (predicates.isEmpty()) {
			return new Criteria();
		}
		return new Criteria().andOperator(predicates.toArray(new Criteria[0]));
	}
}
