package org.cekpelunasan.service.simulasi;

import com.opencsv.CSVReader;
import org.cekpelunasan.entity.Simulasi;
import org.cekpelunasan.entity.SimulasiResult;
import org.cekpelunasan.repository.SimulasiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Service
public class SimulasiService {
    private static final Logger log = LoggerFactory.getLogger(SimulasiService.class);
    private static final String SEQUENCE_INTEREST = "I";
    private static final String SEQUENCE_PRINCIPAL = "P";
    private static final int LATE_PAYMENT_THRESHOLD = 90;
    
    private final SimulasiRepository simulasiRepository;

    public SimulasiService(SimulasiRepository simulasiRepository) {
        this.simulasiRepository = simulasiRepository;
    }
    
    private List<Simulasi> findSimulasiBySpk(String spk) {
        return simulasiRepository.findBySpk(spk);
    }

    public SimulasiResult getSimulasi(String spk, Long userInput) {
        List<Simulasi> keterlambatan = findSimulasiBySpk(spk);
        if (keterlambatan.isEmpty()) {
            return new SimulasiResult();
        }

        keterlambatan.sort(Comparator.comparing(Simulasi::getKeterlambatan).reversed());
        long maxLateDay = keterlambatan.stream()
                .filter(t -> t.getTunggakan() > 0)
                .mapToLong(Simulasi::getKeterlambatan)
                .max()
                .orElse(0L);
        
        AtomicLong remainingBalance = new AtomicLong(userInput);
        AtomicLong principalPayment = new AtomicLong(0L);
        AtomicLong interestPayment = new AtomicLong(0L);
        
        log.info("Max late days: {}, which results in {}", maxLateDay, maxLateDay <= LATE_PAYMENT_THRESHOLD);

        // Determine payment sequence based on late days
        String firstSequence = maxLateDay <= LATE_PAYMENT_THRESHOLD ? SEQUENCE_INTEREST : SEQUENCE_PRINCIPAL;
        String secondSequence = maxLateDay <= LATE_PAYMENT_THRESHOLD ? SEQUENCE_PRINCIPAL : SEQUENCE_INTEREST;
        
        // Process first priority payments
        processPayments(keterlambatan, remainingBalance, 
                        firstSequence.equals(SEQUENCE_INTEREST) ? interestPayment : principalPayment, 
                        simulasi -> simulasi.getSequence().equals(firstSequence));
        
        // Process second priority payments if balance remains
        if (remainingBalance.get() > 0) {
            processPayments(keterlambatan, remainingBalance, 
                            secondSequence.equals(SEQUENCE_INTEREST) ? interestPayment : principalPayment, 
                            simulasi -> simulasi.getSequence().equals(secondSequence));
        }

        // Calculate remaining late days
        long remainingLateDays = keterlambatan.stream()
                .filter(data -> data.getTunggakan() > 0L)
                .mapToLong(Simulasi::getKeterlambatan)
                .max()
                .orElse(0L);
                
        log.info("Remaining late days: {} days", remainingLateDays);
        
        return SimulasiResult.builder()
                .masukI(interestPayment.get())
                .masukP(principalPayment.get())
                .maxDate(remainingLateDays + getDaysToEndOfMonth())
                .build();
    }
    
    private void processPayments(List<Simulasi> records, AtomicLong balance, 
                                AtomicLong paymentTotal, Predicate<Simulasi> filter) {
        records.stream()
            .filter(filter)
            .forEach(record -> {
                long debt = record.getTunggakan();
                long currentBalance = balance.get();

                if (debt == 0 || currentBalance == 0) return;

                if (currentBalance >= debt) {
                    record.setTunggakan(0L);
                    record.setKeterlambatan(0L);
                    balance.addAndGet(-debt);
                    paymentTotal.addAndGet(debt);
                } else {
                    record.setTunggakan(debt - currentBalance);
                    paymentTotal.addAndGet(currentBalance);
                    balance.set(0);
                }
            });
    }

    public void parseCsv(Path path) {
        simulasiRepository.deleteAll();
        final int BATCH_SIZE = 500;
        final int MAX_CONCURRENT_TASKS = 10;
        
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASKS);
        List<Simulasi> currentBatch = new ArrayList<>(BATCH_SIZE);
        
        try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
            String[] line;
            while ((line = reader.readNext()) != null) {
                currentBatch.add(mapToSimulasi(line));
                
                if (currentBatch.size() >= BATCH_SIZE) {
                    saveCurrentBatchAsync(currentBatch, semaphore, executorService);
                    currentBatch = new ArrayList<>(BATCH_SIZE);
                }
            }
            
            if (!currentBatch.isEmpty()) {
                saveCurrentBatchAsync(currentBatch, semaphore, executorService);
            }
        } catch (Exception e) {
            log.error("Failed to read CSV file: {}", e.getMessage(), e);
        }
    }
    
    private void saveCurrentBatchAsync(List<Simulasi> batch, Semaphore semaphore, 
                                     ExecutorService executorService) {
        List<Simulasi> batchToSave = new ArrayList<>(batch);
        try {
            semaphore.acquire();
            executorService.submit(() -> {
                try {
                    simulasiRepository.saveAll(batchToSave);
                } catch (Exception e) {
                    log.error("Error saving batch: {}", e.getMessage(), e);
                } finally {
                    semaphore.release();
                }
            });
        } catch (InterruptedException e) {
            log.error("Thread interrupted while waiting for semaphore: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
    
    private Simulasi mapToSimulasi(String[] lines) {
        return Simulasi.builder()
            .spk(lines[0])
            .tanggal(lines[1])
            .sequence(lines[2])
            .tunggakan(Long.parseLong(lines[3]))
            .denda(Long.parseLong(lines[4]))
            .keterlambatan(Long.parseLong(lines[5]))
            .build();
    }
    
    private long getDaysToEndOfMonth() {
        return ChronoUnit.DAYS.between(LocalDate.now(), YearMonth.now().atEndOfMonth());
    }
    
    public long minimalBayar(String spk) {
        List<Simulasi> records = findSimulasiBySpk(spk);
        
        if (records.isEmpty()) {
            return 0L;
        }
        
        records.sort(Comparator.comparing(Simulasi::getKeterlambatan).reversed());
        long maxLateDay = records.getFirst().getKeterlambatan();
        AtomicLong minimumPayment = new AtomicLong(0L);
        
        if (maxLateDay > LATE_PAYMENT_THRESHOLD) {
            records.stream()
                .filter(this::isPrincipalOrLateInterest)
                .forEach(record -> minimumPayment.addAndGet(record.getTunggakan()));
        } else {
            records.stream()
                .filter(this::isInterestOrLatePrincipal)
                .forEach(record -> minimumPayment.addAndGet(record.getTunggakan()));
        }
        
        return minimumPayment.get();
    }
    
    private boolean isPrincipalOrLateInterest(Simulasi record) {
        return record.getSequence().equals(SEQUENCE_PRINCIPAL) || 
               (record.getSequence().equals(SEQUENCE_INTEREST) && 
                record.getKeterlambatan() > LATE_PAYMENT_THRESHOLD);
    }
    
    private boolean isInterestOrLatePrincipal(Simulasi record) {
        return record.getSequence().equals(SEQUENCE_INTEREST) || 
               (record.getSequence().equals(SEQUENCE_PRINCIPAL) && 
                record.getKeterlambatan() > LATE_PAYMENT_THRESHOLD);
    }
}