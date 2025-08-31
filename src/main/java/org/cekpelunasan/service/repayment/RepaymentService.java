package org.cekpelunasan.service.repayment;

import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.repository.RepaymentRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
@Deprecated
public class RepaymentService {

	private final RepaymentRepository repaymentRepository;

	public RepaymentService(RepaymentRepository repaymentRepository) {
		this.repaymentRepository = repaymentRepository;
	}


	public Repayment findRepaymentById(Long id) {
		return repaymentRepository.findById(id).orElse(null);
	}
	@Transactional
	public void deleteAll() {
		repaymentRepository.deleteAllFast();
	}

	public void parseCsvAndSaveIntoDatabase(Path path) {
		final int BATCH_SIZE = 500;
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		List<Future<?>> futures = new ArrayList<>();

		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			List<Repayment> batch = new ArrayList<>(BATCH_SIZE);

			while ((line = reader.readNext()) != null) {
				Repayment repayment = Repayment.builder()
					.customerId(line[0])
					.product(line[1])
					.name(line[2])
					.address(line[3])
					.amount(Long.parseLong(line[4]))
					.interest(Long.parseLong(line[5]))
					.sistem(Long.parseLong(line[6]))
					.penaltyLoan(Long.parseLong(line[7]))
					.penaltyRepayment(Long.parseLong(line[8]))
					.totalPay(Long.parseLong(line[9]))
					.branch(line[10])
					.startDate(line[11])
					.plafond(Long.parseLong(line[12]))
					.lpdb(line[13])
					.createdAt(Date.from(Instant.now()))
					.build();
				batch.add(repayment);

				if (batch.size() >= BATCH_SIZE) {
					List<Repayment> batchToSave = new ArrayList<>(batch); // Create a copy
					futures.add(executor.submit(() -> {
						repaymentRepository.saveAll(batchToSave);
						return null;
					}));
					batch.clear();
				}
			}

			// Process any remaining items in the last batch
			if (!batch.isEmpty()) {
				List<Repayment> batchToSave = new ArrayList<>(batch);
				futures.add(executor.submit(() -> {
					repaymentRepository.saveAll(batchToSave);
					return null;
				}));
			}

			// Wait for all virtual threads to complete
			int savedCount = 0;
			for (Future<?> future : futures) {
				try {
					future.get();
					savedCount += BATCH_SIZE;
				} catch (Exception e) {
					log.error("Error waiting for batch to complete: {}", e.getMessage(), e);
				}
			}

			log.info("✅ Upload selesai: ~{} data disimpan", savedCount);

		} catch (Exception e) {
			log.warn("❌ Gagal upload CSV: {}", e.getMessage(), e);
		} finally {
			executor.shutdown();
		}
	}

	public Repayment findAll() {
		return repaymentRepository.findAll().getFirst();
	}

	@Cacheable(value = "repayment", key = "#name")
	public Page<Repayment> findName(String name, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		return repaymentRepository.findByNameContainingIgnoreCase(name, pageable);
	}

	public int countAll() {
		return Math.toIntExact(repaymentRepository.count());
	}


	public List<Repayment> findAllLimited() {
		Pageable pageable = PageRequest.of(0, 10);
		return repaymentRepository.findAll(pageable).getContent();
	}

	public List<Repayment> searchByIdOrNameLimited(String searchTerm) {
		Pageable pageable = PageRequest.of(0, 10);
		return repaymentRepository.findByCustomerIdContainingIgnoreCaseOrNameContainingIgnoreCase(
			searchTerm, searchTerm, pageable
		);
	}
}