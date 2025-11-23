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

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotKolekService {

	private final PayingRepository payingRepository;
    private final BillsRepository billsRepository;


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
    
    public List<Bills> findDueDate(String branch) {
        return filterBills(branch, 
            (repo, br) -> repo.findByDueDateContaining(getMonth()));
    }
    
    public List<Bills> findFirstPay(String branch) {
		log.info("Finding {}", getLastMonth());
        return filterBills(branch, 
            (repo, br) -> repo.findByRealizationIsContainingIgnoreCase(getLastMonth()));
    }
    
    public List<Bills> findMinimalPay(String branch) {
		List<Bills> bills = filterBills(branch,
			(repo, br) -> new ArrayList<>(repo.findByMinInterestOrMinPrincipalIsGreaterThanAndBranch(
				0L, 0L, br, Pageable.unpaged()).stream().toList()));
		log.info("Found {} bills", bills.size());
		bills.removeIf(this::isValidBillsHotKolek);
		log.info("Found {} bills from Filter", bills.size());
		return bills;
	}
    
    private List<Bills> filterBills(String branch, BiFunction<BillsRepository, String, List<Bills>> billsFetcher) {
        List<String> payings = billsRepository.findUnpaidBillsByBranch(branch).stream().map(Bills::getNoSpk).toList();
        List<Bills> bills = billsFetcher.apply(billsRepository, branch);
        bills.removeIf(bill -> payings.contains(bill.getNoSpk()));
        bills.removeIf(bill -> !bill.getOfficeLocation().equals(branch));
        return bills;
    }

	@Transactional
	public void saveAllPaying(List<String> list) {
		payingRepository.saveAll(list.stream().map(spk -> Paying.builder()
			.id(spk)
			.paid(true)
			.build())
			.toList());
	}
	@Transactional
	public void deleteAllPaying() {
		payingRepository.deleteAll();
	}

	@Transactional
	public void deleteAllPaying(List<String> spkList) {
		payingRepository.deleteAllById(spkList);
	}

	private boolean isValidBillsHotKolek(Bills bills) {
		if (bills == null) {
			return true;
		}

		if (bills.getMinInterest() <= 0 && bills.getMinPrincipal() <= 0) {
			return true;
		}


		if (bills.getMinInterest() > 0) {
			if (bills.getMinInterest() > bills.getInterest()) {
				return true;
			}
		}


		if (bills.getMinPrincipal() > 0) {
			if (bills.getMinPrincipal() > bills.getPrincipal()) {
				return true;
			}
		}
		return Integer.parseInt(bills.getDayLate()) > 120;
	}
}