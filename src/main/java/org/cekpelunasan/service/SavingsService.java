package org.cekpelunasan.service;

import com.opencsv.CSVReader;
import org.cekpelunasan.entity.Savings;
import org.cekpelunasan.repository.SavingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SavingsService {

    private static final Logger log = LoggerFactory.getLogger(SavingsService.class);
    private final SavingsRepository savingsRepository;

    public SavingsService(SavingsRepository savingsRepository) {
        this.savingsRepository = savingsRepository;
    }

    @Transactional
    public void batchSavingAccounts(List<Savings> savingsList) {
        savingsRepository.saveAll(savingsList);
    }
    public Page<Savings> findByNameAndBranch(String name, String branch, int page) {
        Pageable pageable = PageRequest.of(page, 5);
        return savingsRepository.findByNameContainingIgnoreCaseAndBranch(name, branch, pageable);
    }
    public void parseCsvAndSaveIntoDatabase(Path path) {
        savingsRepository.deleteAll();
        final int BATCH_SIZE = 1000;
        final int MAX_CONCURRENT_TASK = Runtime.getRuntime().availableProcessors() * 2;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASK);
        List<Savings> currentBatch = new ArrayList<>(BATCH_SIZE);
        int counter = 0;

        try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))){
            String[] line;
            while ((line = reader.readNext()) != null) {
                currentBatch.add(mapToSavings(line));
                if (currentBatch.size() >= BATCH_SIZE) {
                    List<Savings> batchToSave = new ArrayList<>(currentBatch);
                    semaphore.acquire();
                    executor.submit(() -> {
                        try {
                            batchSavingAccounts(batchToSave);
                        } catch (Exception e) {
                            log.error("Error {}", e.getMessage());
                        } finally {
                            semaphore.release();
                        }
                    });
                    counter += BATCH_SIZE;
                    System.out.println("Submitted Batch " + counter);
                    currentBatch.clear();
                }
            }
            if (!currentBatch.isEmpty()) {
                List<Savings> batchToSave = new ArrayList<>(currentBatch);
                semaphore.acquire();
                executor.submit(() -> {
                    try {
                        batchSavingAccounts(batchToSave);
                    } catch (Exception e) {
                        log.error("Error Save {}", e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                });
                System.out.println("Submitted final batch: " + (counter + currentBatch.size()));
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Error parsing CSV file: {}", e.getMessage());
        }

        System.out.println("CSV file parsed and data saved to database successfully.");
    }
    public Savings mapToSavings(String[] line) {
        return Savings.builder()
                .branch(line[0])
                .type(line[1])
                .cif(line[2])
                .tabId(line[3])
                .name(line[4])
                .address(line[5])
                .balance(BigDecimal.valueOf(Long.parseLong(line[6])))
                .transaction(BigDecimal.valueOf(Long.parseLong(line[7])))
                .accountOfficer(line[8])
                .phone(line[9])
                .minimumBalance(BigDecimal.valueOf(Long.parseLong(line[10])))
                .blockingBalance(BigDecimal.valueOf(Long.parseLong(line[11])))
                .build();
    }
    public Set<String> listAllBranch() {
        return savingsRepository.findByBranch().stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
