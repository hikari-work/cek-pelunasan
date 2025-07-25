package org.cekpelunasan.service.Bill;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Paying;
import org.cekpelunasan.repository.BillsRepository;
import org.cekpelunasan.repository.PayingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class HotKolekService {

	private static final Logger log = LoggerFactory.getLogger(HotKolekService.class);
	private final PayingRepository payingRepository;
    private final BillsRepository billsRepository;

    @Transactional
    public void savePaying(String spk) {
        payingRepository.save(Paying.builder()
                .id(spk)
                .paid(true)
            .build());
    }
    
    @Transactional
    public void deletePaying() {
        payingRepository.deleteAll();
    }

	private String getMonth() {
		YearMonth month = YearMonth.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy");
		return formatter.format(month);
	}
	private String getLastMonth() {
		YearMonth month = YearMonth.now().minusMonths(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy");
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
		bills.removeIf(bill -> bill.getAccountOfficer().equals("JKS"));
		return bills;
	}
    
    private List<Bills> filterBills(String branch, BiFunction<BillsRepository, String, List<Bills>> billsFetcher) {
        List<String> payings = payingRepository.findAll().stream().map(Paying::getId).toList();
        List<Bills> bills = billsFetcher.apply(billsRepository, branch);
        bills.removeIf(bill -> payings.contains(bill.getNoSpk()));
        bills.removeIf(bill -> !bill.getOfficeLocation().equals(branch));
        return bills;
    }

	public void saveAllPaying(List<String> list) {
		payingRepository.saveAll(list.stream().map(spk -> Paying.builder()
			.id(spk)
			.paid(true)
			.build())
			.toList());
	}
}