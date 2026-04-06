package org.cekpelunasan.core.service.bill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.Paying;
import org.cekpelunasan.core.repository.BillsRepository;
import org.cekpelunasan.core.repository.PayingRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotKolekService {

	private final PayingRepository payingRepository;
	private final BillsRepository billsRepository;
	private static final String TARGET_BRANCH = "1075";

	private String getMonth() {
		YearMonth month = YearMonth.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
		return formatter.format(month);
	}

	private String getLastMonth() {
		YearMonth month = YearMonth.now().minusMonths(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
		return formatter.format(month);
	}

	public Mono<List<Bills>> findDueDate(String kiosCode) {
		log.debug("Finding due date bills for kios: '{}', month: '{}'", kiosCode, getMonth());
		return billsRepository.findByBranchAndDueDateContaining(TARGET_BRANCH, getMonth())
			.collectList()
			.flatMap(bills -> filterBillsByKios(bills, kiosCode));
	}

	public Mono<List<Bills>> findFirstPay(String kiosCode) {
		log.debug("Finding first pay bills for kios: '{}', lastMonth: '{}'", kiosCode, getLastMonth());
		return billsRepository.findByBranchAndRealizationContaining(TARGET_BRANCH, getLastMonth())
			.collectList()
			.flatMap(bills -> filterBillsByKios(bills, kiosCode));
	}

	public Mono<List<Bills>> findMinimalPay(String kiosCode) {
		log.debug("Getting minimal pay bills for kios: '{}'", kiosCode);
		Mono<List<Bills>> billsMono;
		if (TARGET_BRANCH.equals(kiosCode) || kiosCode == null || kiosCode.trim().isEmpty()) {
			billsMono = billsRepository.findByBranchAndTotalMin(TARGET_BRANCH, 0L, Pageable.unpaged()).collectList();
		} else {
			billsMono = billsRepository.findByBranchAndKiosAndTotalMin(TARGET_BRANCH, kiosCode, 0L, Pageable.unpaged()).collectList();
		}
		return billsMono
			.defaultIfEmpty(List.of())
			.flatMap(bills -> filterBillsByKios(bills, kiosCode))
			.map(filtered -> {
				filtered.removeIf(this::isNotValidBills);
				log.debug("Found {} bills after validation", filtered.size());
				return filtered;
			});
	}

	public Mono<List<Bills>> findUnpaidBillsByBranch(String branch) {
		return payingRepository.findAll()
			.map(Paying::getId)
			.collectList()
			.flatMap(paidIds -> {
				if (paidIds.isEmpty()) {
					return billsRepository.findAllByBranch(branch).collectList();
				}
				return billsRepository.findByBranchAndNoSpkNotIn(branch, paidIds).collectList();
			});
	}

	private Mono<List<Bills>> filterBillsByKios(List<Bills> bills, String kiosCode) {
		log.debug("Filtering bills for kios: '{}', input size: {}", kiosCode, bills.size());
		if (bills.isEmpty()) {
			return Mono.just(bills);
		}
		return payingRepository.findAll().map(Paying::getId).collectList()
			.map(paidSpks -> {
				List<Bills> mutableBills = new ArrayList<>(bills);
				int beforePayingFilter = mutableBills.size();
				mutableBills.removeIf(bill -> paidSpks.contains(bill.getNoSpk()));
				log.debug("After paid filter: {} -> {}", beforePayingFilter, mutableBills.size());

				int beforeKiosFilter = mutableBills.size();
				if (TARGET_BRANCH.equals(kiosCode)) {
					mutableBills.removeIf(bill -> {
						String billKios = bill.getKios();
						boolean isEmptyOrNull = billKios == null || billKios.trim().isEmpty();
						log.trace("Bill {} kios: '{}', isEmptyOrNull: {}", bill.getNoSpk(), billKios, isEmptyOrNull);
						return !isEmptyOrNull;
					});
					log.debug("After kios NULL/empty filter (for branch code): {} -> {}", beforeKiosFilter, mutableBills.size());
				} else if (kiosCode != null && !kiosCode.trim().isEmpty()) {
					mutableBills.removeIf(bill -> !kiosCode.equals(bill.getKios()));
					log.debug("After kios '{}' filter: {} -> {}", kiosCode, beforeKiosFilter, mutableBills.size());
				}
				int beforeBranchFilter = mutableBills.size();
				mutableBills.removeIf(bill -> !TARGET_BRANCH.equals(bill.getBranch()));
				log.debug("After branch 1075 filter: {} -> {}", beforeBranchFilter, mutableBills.size());

				return mutableBills;
			});
	}

	public Mono<Void> saveAllPaying(@NonNull List<String> list) {
		log.debug("Saving {} paying records", list.size());
		return payingRepository.saveAll(list.stream()
			.map(spk -> Paying.builder()
				.id(spk)
				.paid(true)
				.build())
			.toList())
			.then()
			.doOnSuccess(v -> log.debug("Successfully saved {} paying records", list.size()));
	}

	private boolean isNotValidBills(Bills bills) {
		if (bills == null) {
			return true;
		}
		try {
			int dayLate = Integer.parseInt(bills.getDayLate());
			if (dayLate > 125) {
				log.info("Removed {} because above 120 days", bills.getName());
				return true;
			}
		} catch (NumberFormatException e) {
			log.info("Removed {} number exception ", bills.getName());
			return true;
		}
		log.info("Selecting {}", bills.getName());
		return false;
	}
}
