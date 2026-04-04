package org.cekpelunasan.core.service.bill;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.repository.BillsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BillService {

	private static final Logger log = LoggerFactory.getLogger(BillService.class);
	private final BillsRepository billsRepository;
	private final ReactiveMongoTemplate mongoTemplate;

	public Mono<Set<String>> lisAllBranch() {
		return mongoTemplate.findDistinct("branch", Bills.class, String.class)
			.collectList()
			.map(list -> new LinkedHashSet<>(Objects.requireNonNullElse(list, List.of())));
	}

	public Mono<Bills> getBillById(@NonNull String id) {
		return billsRepository.findById(id);
	}

	public Mono<Long> countAllBills() {
		return billsRepository.count();
	}

	public Mono<List<Bills>> findAllBillsByBranch(String branch) {
		return billsRepository.findAllByBranch(branch).collectList();
	}

	public Mono<Page<Bills>> findDueDateByAccountOfficer(String accountOfficer, String payDown, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByAccountOfficerAndPayDown(accountOfficer, payDown, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByAccountOfficerAndPayDown(accountOfficer, payDown).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	public Mono<Page<Bills>> findBranchAndPayDown(String branch, String payDown, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByBranchAndPayDownOrderByAccountOfficer(branch, payDown, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByBranchAndPayDown(branch, payDown).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	public Mono<Page<Bills>> findByNameAndBranch(String name, String branch, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByNameContainingIgnoreCaseAndBranch(name, branch, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByNameContainingIgnoreCaseAndBranch(name, branch).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	public Mono<Page<Bills>> findMinimalPaymentByBranch(String branch, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		return billsRepository.findByKiosAndTotalMin(branch, 0L, pageRequest).collectList()
			.map(content -> new PageImpl<>(content, pageRequest, 0L));
	}

	public Mono<Page<Bills>> findMinimalPaymentByAccountOfficer(String officer, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(0L, 0L, officer, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(0L, 0L, officer).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	public Mono<Set<String>> findAllAccountOfficer() {
		return mongoTemplate.findDistinct("accountOfficer", Bills.class, String.class)
			.collectList()
			.map(list -> new LinkedHashSet<>(Objects.requireNonNullElse(list, List.of())));
	}

	public Mono<Void> parseCsvAndSaveIntoDatabase(Path path) {
		return billsRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						String[] line;
						while ((line = reader.readNext()) != null) {
							sink.next(line);
						}
						sink.complete();
					} catch (Exception e) {
						log.error("Error reading CSV: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToBill)
				.buffer(500)
				.flatMap(batch -> billsRepository.saveAll(batch).then()
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc());
	}

	public Mono<Void> deleteAll() {
		return billsRepository.deleteAll();
	}

	private Bills mapToBill(String[] line) {
		return Bills.builder()
			.customerId(line[0])
			.wilayah(line[1])
			.branch(line[2])
			.noSpk(line[3])
			.officeLocation(line[4])
			.product(line[5])
			.name(line[6])
			.address(line[7])
			.payDown(line[8])
			.realization(line[9])
			.dueDate(line[10])
			.collectStatus(line[11])
			.dayLate(line[12])
			.plafond(parseLong(line[13]))
			.debitTray(parseLong(line[14]))
			.interest(parseLong(line[15]))
			.principal(parseLong(line[16]))
			.installment(parseLong(line[17]))
			.lastInterest(parseLong(line[18]))
			.lastPrincipal(parseLong(line[19]))
			.lastInstallment(parseLong(line[20]))
			.fullPayment(parseLong(line[21]))
			.minInterest(parseLong(line[22]))
			.minPrincipal(parseLong(line[23]))
			.penaltyInterest(parseLong(line[24]))
			.penaltyPrincipal(parseLong(line[25]))
			.accountOfficer(line[26])
			.kios(line[28])
			.titipan(parseLong(line[29]))
			.fixedInterest(parseLong(line[30]))
			.build();
	}

	private long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
