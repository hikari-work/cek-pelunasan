package org.cekpelunasan.service.customerhistory;

import com.opencsv.CSVReader;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.CustomerHistory;
import org.cekpelunasan.repository.CustomerHistoryRepository;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerHistoryService {

	private final CustomerHistoryRepository customerHistoryRepository;

	public List<Long> findCustomerIdAndReturnListOfCollectNumber(String customerId) {
		List<Object[]> result = customerHistoryRepository.countCollectStatusByCustomer(customerId);
		if (result.isEmpty()) {
			return Collections.emptyList();
		}
		Object[] row = result.getFirst();
		return Arrays.stream(row)
			.map(val -> val != null ? ((Number) val).longValue() : 0L)
			.collect(Collectors.toList());
	}

	public void parseCsvAndSaveIntoDatabase(Path path) {

		final int BATCH_SIZE = 500;
		final int MAX_CONCURRENT_TASK = Runtime.getRuntime().availableProcessors() * 2;

		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASK);
		List<CustomerHistory> currentBatch = new ArrayList<>(BATCH_SIZE);
		int counter = 0;

		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			while ((line = reader.readNext()) != null) {
				currentBatch.add(mapToCustomerHistory(line));

				if (currentBatch.size() >= BATCH_SIZE) {
					List<CustomerHistory> batchToSave = new ArrayList<>(currentBatch);
					semaphore.acquire();
					executor.submit(() -> {
						try {
							saveBatch(batchToSave);
						} catch (Exception e) {
							log.error("Gagal simpan batch", e);
						} finally {
							semaphore.release();
						}
					});
					counter += BATCH_SIZE;
					System.out.println("Submitted batch: " + counter);
					currentBatch.clear();
				}
			}

			// Sisa data terakhir
			if (!currentBatch.isEmpty()) {
				List<CustomerHistory> batchToSave = new ArrayList<>(currentBatch);
				semaphore.acquire();
				executor.submit(() -> {
					try {
						saveBatch(batchToSave);
					} catch (Exception e) {
						log.error("Gagal simpan final batch", e);
					} finally {
						semaphore.release();
					}
				});
				System.out.println("Submitted final batch: " + (counter + currentBatch.size()));
			}

			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.HOURS);
		} catch (Exception e) {
			log.error("Gagal membaca file CSV: {}", e.getMessage(), e);
		}

		System.out.println("Semua data telah disimpan.");
	}

	@Transactional
	public void saveBatch(List<CustomerHistory> batch) {
		customerHistoryRepository.saveAll(batch);
	}

	public CustomerHistory mapToCustomerHistory(String[] line) {
		return CustomerHistory.builder()
			.customerId(line[0])
			.collectStatus(line[1])
			.build();
	}
}
