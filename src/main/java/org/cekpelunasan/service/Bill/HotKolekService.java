package org.cekpelunasan.service.Bill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Paying;
import org.cekpelunasan.repository.BillsRepository;
import org.cekpelunasan.repository.PayingRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.NonNull;

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

	public List<Bills> findDueDate(String kiosCode) {
		log.debug("Finding due date bills for kios: '{}', month: '{}'", kiosCode, getMonth());
		List<Bills> bills = billsRepository.findByBranchAndDueDateContaining(TARGET_BRANCH, getMonth());
		log.debug("Raw bills from findByBranchAndDueDateContaining: {}", bills.size());
		return filterBillsByKios(bills, kiosCode);
	}

	public List<Bills> findFirstPay(String kiosCode) {
		log.debug("Finding first pay bills for kios: '{}', lastMonth: '{}'", kiosCode, getLastMonth());
		List<Bills> bills = billsRepository.findByBranchAndRealizationContaining(TARGET_BRANCH, getLastMonth());
		log.debug("Raw bills from findByBranchAndRealizationContaining: {}", bills.size());
		return filterBillsByKios(bills, kiosCode);
	}

	public List<Bills> findMinimalPay(String kiosCode) {
		log.debug("Getting minimal pay bills for kios: '{}'", kiosCode);

		List<Bills> bills;

		if (TARGET_BRANCH.equals(kiosCode)) {

			bills = new ArrayList<>(
				billsRepository.findByBranchAndTotalMin(TARGET_BRANCH, 0L, Pageable.unpaged())
					.getContent()
			);
			log.debug("Query: findByBranchAndTotalMin(branch={}, minTotal=0) - will filter for NULL/empty kios", TARGET_BRANCH);
		} else if (kiosCode == null || kiosCode.trim().isEmpty()) {
			bills = new ArrayList<>(
				billsRepository.findByBranchAndTotalMin(TARGET_BRANCH, 0L, Pageable.unpaged())
					.getContent()
			);
			log.debug("Query: findByBranchAndTotalMin(branch={}, minTotal=0) - no kios filter", TARGET_BRANCH);
		} else {
			bills = new ArrayList<>(
				billsRepository.findByBranchAndKiosAndTotalMin(TARGET_BRANCH, kiosCode, 0L, Pageable.unpaged())
					.getContent()
			);
			log.debug("Query: findByBranchAndKiosAndTotalMin(branch={}, kios={}, minTotal=0)", TARGET_BRANCH, kiosCode);
		}

		log.debug("Raw bills from repository: {}", bills.size());

		List<Bills> filteredBills = filterBillsByKios(bills, kiosCode);
		log.debug("Total bills after filterBillsByKios: {}", filteredBills.size());

		filteredBills.removeIf(this::isNotValidBills);
		log.debug("Found {} bills after validation", filteredBills.size());

		return filteredBills;
	}

	private List<Bills> filterBillsByKios(List<Bills> bills, String kiosCode) {
		log.debug("Filtering bills for kios: '{}', input size: {}", kiosCode, bills.size());

		if (bills.isEmpty()) {
			log.debug("No bills to filter, returning empty list");
			return bills;
		}

		List<String> paidSpks = payingRepository.findAll()
			.stream()
			.map(Paying::getId)
			.toList();

		log.debug("Found {} paid bills to exclude", paidSpks.size());

		int beforePayingFilter = bills.size();
		bills.removeIf(bill -> paidSpks.contains(bill.getNoSpk()));
		log.debug("After paid filter: {} -> {}", beforePayingFilter, bills.size());

		int beforeKiosFilter = bills.size();

		if (TARGET_BRANCH.equals(kiosCode)) {
			bills.removeIf(bill -> {
				String billKios = bill.getKios();
				boolean isEmptyOrNull = billKios == null || billKios.trim().isEmpty();
				log.trace("Bill {} kios: '{}', isEmptyOrNull: {}", bill.getNoSpk(), billKios, isEmptyOrNull);
				return !isEmptyOrNull;
			});
			log.debug("After kios NULL/empty filter (for branch code): {} -> {}", beforeKiosFilter, bills.size());
		} else if (kiosCode != null && !kiosCode.trim().isEmpty()) {
			bills.removeIf(bill -> !kiosCode.equals(bill.getKios()));
			log.debug("After kios '{}' filter: {} -> {}", kiosCode, beforeKiosFilter, bills.size());
		}
		int beforeBranchFilter = bills.size();
		bills.removeIf(bill -> !TARGET_BRANCH.equals(bill.getBranch()));
		log.debug("After branch 1075 filter: {} -> {}", beforeBranchFilter, bills.size());

		return bills;
	}

	@Transactional
	public void saveAllPaying(@NonNull List<String> list) {
		log.debug("Saving {} paying records", list.size());
		payingRepository.saveAll(list.stream()
			.map(spk -> Paying.builder()
				.id(spk)
				.paid(true)
				.build())
			.toList());
		log.debug("Successfully saved {} paying records", list.size());
	}

	private boolean isNotValidBills(Bills bills) {
		if (bills == null) {
			return true;
		}
		if (bills.getMinInterest() <= 0 && bills.getMinPrincipal() <= 0) {
			log.info("Removed {}", bills.getName());
			return true;
		}
		if (bills.getMinInterest() > 0 && bills.getMinInterest() > bills.getInterest()) {
			log.info("Removed {}", bills.getName());
			return true;
		}
		if (bills.getMinPrincipal() > 0 && bills.getMinPrincipal() > bills.getPrincipal()) {
			log.info("Removed {}", bills.getName());
			return true;
		}

		try {
			int dayLate = Integer.parseInt(bills.getDayLate());
			if (dayLate > 120) {
				log.info("Removed {}", bills.getName());
				return true;
			}
		} catch (NumberFormatException e) {
			log.info("Removed {}", bills.getName());
			return true;
		}
		log.info("Selecting {}", bills.getName());
		return false;
	}
}