package org.cekpelunasan.core.service.customerhistory;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.CustomerHistory;
import org.cekpelunasan.core.repository.CustomerHistoryRepository;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerHistoryService {

	private final CustomerHistoryRepository customerHistoryRepository;
	private final ReactiveMongoTemplate mongoTemplate;

	public Mono<List<Long>> findCustomerIdAndReturnListOfCollectNumber(String customerId) {
		AggregationOperation match = Aggregation.match(Criteria.where("customerId").is(customerId));
		AggregationOperation group = Aggregation.group()
			.sum(ConditionalOperators.when(Criteria.where("collectStatus").is("01")).then(1).otherwise(0)).as("count01")
			.sum(ConditionalOperators.when(Criteria.where("collectStatus").is("02")).then(1).otherwise(0)).as("count02")
			.sum(ConditionalOperators.when(Criteria.where("collectStatus").is("03")).then(1).otherwise(0)).as("count03")
			.sum(ConditionalOperators.when(Criteria.where("collectStatus").is("04")).then(1).otherwise(0)).as("count04")
			.sum(ConditionalOperators.when(Criteria.where("collectStatus").is("05")).then(1).otherwise(0)).as("count05");

		return mongoTemplate.aggregate(
			Aggregation.newAggregation(match, group),
			CustomerHistory.class,
			org.bson.Document.class
		).collectList()
			.map(results -> {
				if (results == null || results.isEmpty()) return List.<Long>of();
				org.bson.Document doc = results.get(0);
				List<Long> counts = new ArrayList<>();
				for (String key : List.of("count01", "count02", "count03", "count04", "count05")) {
					Object val = doc.get(key);
					counts.add(val != null ? ((Number) val).longValue() : 0L);
				}
				return counts;
			});
	}

	public Mono<Long> countCustomerHistory() {
		return customerHistoryRepository.count();
	}

	public Mono<Void> parseCsvAndSaveIntoDatabase(Path path) {
		return customerHistoryRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						String[] line;
						while ((line = reader.readNext()) != null) {
							sink.next(line);
						}
						sink.complete();
					} catch (Exception e) {
						log.error("Gagal membaca file CSV: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToCustomerHistory)
				.buffer(500)
				.flatMap(batch -> saveBatch(batch)
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc())
			.doOnSuccess(v -> log.info("Semua data customer history telah disimpan."));
	}

	public Mono<Void> saveBatch(@NonNull List<CustomerHistory> batch) {
		return customerHistoryRepository.saveAll(batch).then();
	}

	public CustomerHistory mapToCustomerHistory(String[] line) {
		return CustomerHistory.builder()
			.customerId(line[0])
			.collectStatus(line[1])
			.build();
	}
}
