package org.cekpelunasan.core.service.credithistory;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.CreditHistory;
import org.cekpelunasan.core.repository.CreditHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreditHistoryService {

	private static final Logger log = LoggerFactory.getLogger(CreditHistoryService.class);
	private final CreditHistoryRepository creditHistoryRepository;
	private final ReactiveMongoTemplate mongoTemplate;

	public Mono<Page<CreditHistory>> searchAddressByKeywords(List<String> keywords, int page) {
		log.info("Searching for address with keywords: {}", keywords);

		Query activeQuery = new Query(Criteria.where("status").regex("^A$", "i"));
		Mono<List<String>> activeIdsMono = mongoTemplate
			.findDistinct(activeQuery, "customerId", CreditHistory.class, String.class)
			.collectList();

		return activeIdsMono.flatMap(activeCustomerIds -> {
			List<Criteria> addressPredicates = keywords.stream()
				.map(String::trim)
				.filter(word -> !word.isEmpty())
				.map(word -> Criteria.where("address").regex(word, "i"))
				.collect(Collectors.toList());

			Criteria addressCriteria = addressPredicates.isEmpty()
				? new Criteria()
				: new Criteria().andOperator(addressPredicates.toArray(new Criteria[0]));

			Criteria notInActive = Criteria.where("customerId").nin(activeCustomerIds);
			Criteria combined = new Criteria().andOperator(addressCriteria, notInActive);

			Pageable pageable = PageRequest.of(page, 5);
			Query contentQuery = new Query(combined).with(pageable);
			Query countQuery = new Query(combined);

			Mono<List<CreditHistory>> content = mongoTemplate.find(contentQuery, CreditHistory.class).collectList();
			Mono<Long> total = mongoTemplate.count(countQuery, CreditHistory.class).defaultIfEmpty(0L);

			return Mono.zip(content, total)
				.map(t -> {
					log.info("Query returned {} results", t.getT2());
					return (Page<CreditHistory>) new PageImpl<>(t.getT1(), pageable, t.getT2());
				});
		});
	}

	public Mono<Void> saveAll(@NonNull List<CreditHistory> creditHistories) {
		return creditHistoryRepository.saveAll(creditHistories).then();
	}

	public Mono<Void> parseCsvAndSaveIt(Path path) {
		return creditHistoryRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						String[] line;
						while ((line = reader.readNext()) != null) {
							sink.next(line);
						}
						sink.complete();
					} catch (Exception e) {
						log.error("Gagal membaca CSV: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToCreditHistory)
				.buffer(500)
				.flatMap(batch -> saveAll(batch)
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc())
			.doOnSuccess(v -> log.info("Credit history selesai disimpan."));
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

	public Mono<Long> countCreditHistory() {
		return creditHistoryRepository.count();
	}
}
