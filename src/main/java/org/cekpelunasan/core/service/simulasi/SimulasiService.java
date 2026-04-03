package org.cekpelunasan.core.service.simulasi;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Simulasi;
import org.cekpelunasan.core.entity.SimulasiResult;
import org.cekpelunasan.core.repository.SimulasiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class SimulasiService {

    private static final Logger log = LoggerFactory.getLogger(SimulasiService.class);
    private static final String SEQUENCE_INTEREST = "I";
    private static final String SEQUENCE_PRINCIPAL = "P";
    private static final int LATE_PAYMENT_THRESHOLD = 90;

    private final SimulasiRepository simulasiRepository;

    public Mono<Map<String, Integer>> findTotalKeterlambatan(String spk) {
        return simulasiRepository.findBySpk(spk).collectList()
            .map(bySpk -> {
                Map<String, Integer> map = new HashMap<>();
                map.put(SEQUENCE_INTEREST, (int) bySpk.stream()
                    .filter(s -> s.getSequence().equals(SEQUENCE_INTEREST) && s.getTunggakan() > 0)
                    .count());
                map.put(SEQUENCE_PRINCIPAL, (int) bySpk.stream()
                    .filter(s -> s.getSequence().equals(SEQUENCE_PRINCIPAL) && s.getTunggakan() > 0)
                    .count());
                return map;
            });
    }

    public Mono<Long> findMaxBayar(String spk) {
        return simulasiRepository.findBySpk(spk).collectList()
            .map(result -> result.stream()
                .mapToLong(Simulasi::getKeterlambatan)
                .max()
                .orElse(0L));
    }

    public Mono<SimulasiResult> getSimulasi(String spk, Long userInput) {
        return simulasiRepository.findBySpk(spk).collectList()
            .map(keterlambatan -> {
                if (keterlambatan.isEmpty()) return new SimulasiResult();

                keterlambatan.sort(Comparator.comparing(Simulasi::getKeterlambatan).reversed());

                long maxLateDay = keterlambatan.stream()
                    .filter(t -> t.getTunggakan() > 0)
                    .mapToLong(Simulasi::getKeterlambatan)
                    .max()
                    .orElse(0L);

                AtomicLong remainingBalance = new AtomicLong(userInput);
                AtomicLong principalPayment = new AtomicLong(0L);
                AtomicLong interestPayment = new AtomicLong(0L);

                boolean isLowLate = maxLateDay <= LATE_PAYMENT_THRESHOLD;
                String firstSequence = isLowLate ? SEQUENCE_INTEREST : SEQUENCE_PRINCIPAL;
                String secondSequence = isLowLate ? SEQUENCE_PRINCIPAL : SEQUENCE_INTEREST;

                processPayments(keterlambatan, remainingBalance,
                    firstSequence.equals(SEQUENCE_INTEREST) ? interestPayment : principalPayment,
                    s -> s.getSequence().equals(firstSequence));

                if (remainingBalance.get() > 0) {
                    processPayments(keterlambatan, remainingBalance,
                        secondSequence.equals(SEQUENCE_INTEREST) ? interestPayment : principalPayment,
                        s -> s.getSequence().equals(secondSequence));
                }

                long remainingLateDays = keterlambatan.stream()
                    .filter(s -> s.getTunggakan() > 0)
                    .mapToLong(Simulasi::getKeterlambatan)
                    .max()
                    .orElse(0L);

                return SimulasiResult.builder()
                    .masukI(interestPayment.get())
                    .masukP(principalPayment.get())
                    .maxDate(remainingLateDays + getDaysToEndOfMonth())
                    .build();
            });
    }

    private void processPayments(List<Simulasi> records, AtomicLong balance,
                                 AtomicLong paymentTotal, Predicate<Simulasi> filter) {
        records.stream().filter(filter).forEach(record -> {
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

    public Mono<Void> deleteAll() {
        return simulasiRepository.deleteAll();
    }

    public Mono<Void> parseCsv(Path path) {
        return simulasiRepository.deleteAll()
            .then(Flux.<String[]>create(sink -> {
                    try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
                        String[] line;
                        while ((line = reader.readNext()) != null) {
                            sink.next(line);
                        }
                        sink.complete();
                    } catch (Exception e) {
                        log.error("Failed to read CSV file: {}", e.getMessage(), e);
                        sink.error(e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::mapToSimulasi)
                .buffer(500)
                .flatMap(batch -> simulasiRepository.saveAll(batch).then()
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
                    Runtime.getRuntime().availableProcessors())
                .then())
            .doFinally(signal -> System.gc());
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

    public Mono<Long> minimalBayar(String spk) {
        return simulasiRepository.findBySpk(spk).collectList()
            .map(records -> {
                if (records.isEmpty()) return 0L;

                records.removeIf(sim -> {
                    log.info("Keterlambatan {} hari", sim.getKeterlambatan());
                    boolean toRemove = sim.getTunggakan() < 1;
                    if (toRemove) log.info("Tunggakan {} hari sudah dihapus", sim.getTunggakan());
                    return toRemove;
                });

                records.sort(Comparator.comparing(Simulasi::getKeterlambatan).reversed());
                if (records.isEmpty()) return 0L;

                long maxLateDay = records.getFirst().getKeterlambatan();
                AtomicLong minimumPayment = new AtomicLong(0L);

                if (maxLateDay > LATE_PAYMENT_THRESHOLD) {
                    log.info("Hari terburuk  {}", maxLateDay);
                    records.stream()
                        .filter(sim -> sim.getSequence().equals(SEQUENCE_PRINCIPAL))
                        .forEach(record -> {
                            minimumPayment.addAndGet(record.getTunggakan());
                            record.setKeterlambatan(0L);
                        });

                    records.removeIf(sim -> sim.getTunggakan() < 1);

                    if (!records.isEmpty() && records.getFirst().getSequence().equals(SEQUENCE_INTEREST)) {
                        addLateDate(records, minimumPayment);
                    }
                } else {
                    log.info("Penambahan {}", getDaysToEndOfMonth());
                    addLateDate(records, minimumPayment);

                    records.stream()
                        .filter(sim -> sim.getSequence().equals(SEQUENCE_PRINCIPAL) && sim.getKeterlambatan() > 90L)
                        .forEach(record -> {
                            minimumPayment.addAndGet(record.getTunggakan());
                            record.setKeterlambatan(0L);
                        });
                }

                return minimumPayment.get();
            });
    }

    private void addLateDate(List<Simulasi> records, AtomicLong minimumPayment) {
        records.forEach(sim -> sim.setKeterlambatan(sim.getKeterlambatan() + getDaysToEndOfMonth()));

        records.stream()
            .filter(sim -> sim.getSequence().equals(SEQUENCE_INTEREST) && sim.getKeterlambatan() > 90L)
            .forEach(record -> {
                minimumPayment.addAndGet(record.getTunggakan());
                record.setKeterlambatan(0L);
            });
    }
}
