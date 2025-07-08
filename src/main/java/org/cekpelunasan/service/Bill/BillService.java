package org.cekpelunasan.service.Bill;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.repository.BillsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
public class BillService {

	private static final Logger log = LoggerFactory.getLogger(BillService.class);
	private final BillsRepository billsRepository;


	public Set<String> lisAllBranch() {
		return billsRepository.findDistinctBranchByBrach();
	}

	public Bills getBillById(String id) {
		return billsRepository.findById(id).orElse(null);
	}

	@Transactional
	public Long countAllBills() {
		return billsRepository.count();
	}


	public Page<Bills> findDueDateByAccountOfficer(String accountOfficer, String payDown, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		return billsRepository.findByAccountOfficerAndPayDown(accountOfficer, payDown, pageRequest);
	}

	public Page<Bills> findBranchAndPayDown(String branch, String payDown, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		return billsRepository.findByBranchAndPayDownOrderByAccountOfficer(branch, payDown, pageRequest);
	}

	public Page<Bills> findByNameAndBranch(String name, String branch, int page, int size) {
		return billsRepository.findByNameContainingIgnoreCaseAndBranch(name, branch, PageRequest.of(page, size));
	}

	public Page<Bills> findMinimalPaymentByBranch(String branch, int page, int size) {
		return billsRepository.findByMinInterestOrMinPrincipalIsGreaterThanAndBranch(0L, 0L, branch, PageRequest.of(page, size));
	}

	public Page<Bills> findMinimalPaymentByAccountOfficer(String officer, int page, int size) {
		return billsRepository.findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(0L, 0L, officer, PageRequest.of(page, size));
	}

	public Set<String> findAllAccountOfficer() {
		return billsRepository.findDistinctByAccountOfficer();
	}


	public void parseCsvAndSaveIntoDatabase(Path path) {
		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		final int BATCH_SIZE = 1000;


		List<Future<?>> futures = new ArrayList<>();
		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			List<Bills> batch = new ArrayList<>(BATCH_SIZE);
			while ((line = reader.readNext()) != null) {
				batch.add(mapToBill(line));
				if (batch.size() >= BATCH_SIZE) {
					List<Bills> batchSave = new ArrayList<>(batch);
					futures.add(executorService.submit(() -> {
						billsRepository.saveAll(batchSave);
						return null;
					}));
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				List<Bills> batchSave = new ArrayList<>(batch);
				futures.add(executorService.submit(() -> {
					billsRepository.saveAll(batchSave);
					return null;
				}));
			}
			for (Future<?> future : futures) {
				future.get();
			}
		} catch (Exception e) {
			log.error("Error saving to database: {}", e.getMessage(), e);
		} finally {
			executorService.shutdown();
			System.gc();
		}

	}
	@Transactional
	public void deleteAll() {
		billsRepository.deleteAllFast();
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
			.build();
	}

	private long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			log.warn("Failed to parse long from '{}'", value);
			return 0L;
		}
	}
}
