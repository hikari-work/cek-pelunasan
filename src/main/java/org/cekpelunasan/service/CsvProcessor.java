package org.cekpelunasan.service;

import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.repository.BillsRepository;
import org.cekpelunasan.repository.RepaymentRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import static java.lang.Long.parseLong;

@Service
public class CsvProcessor {

    private final RepaymentRepository repaymentRepository;
    private final SimpMessagingTemplate template;
    private final BillsRepository billsRepository;

    public CsvProcessor(SimpMessagingTemplate template, RepaymentRepository repaymentRepository, BillsRepository billsRepository) {
        this.template = template;
        this.repaymentRepository = repaymentRepository;
        this.billsRepository = billsRepository;
    }

    public void processPelunasan(MultipartFile file) {
        repaymentRepository.deleteAll();

        new Thread(() -> {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
                    CSVParser parser = CSVFormat.DEFAULT.withSkipHeaderRecord(false).parse(reader)
            ) {
                List<Repayment> buffer = new ArrayList<>();
                int batchSize = 200;
                int total = 0;

                for (CSVRecord record : parser) {
                    if (record.size() < 14) continue;

                    Repayment repayment = Repayment.builder()
                            .customerId(record.get(0))
                            .product(record.get(1))
                            .name(record.get(2))
                            .address(record.get(3))
                            .amount(parseLong(record.get(4)))
                            .interest(parseLong(record.get(5)))
                            .sistem(parseLong(record.get(6)))
                            .penaltyLoan(parseLong(record.get(7)))
                            .penaltyRepayment(parseLong(record.get(8)))
                            .totalPay(parseLong(record.get(9)))
                            .branch(record.get(10))
                            .startDate(record.get(11))
                            .plafond(parseLong(record.get(12)))
                            .lpdb(record.get(13))
                            .createdAt(Date.from(Instant.now()))
                            .build();

                    buffer.add(repayment);
                    total++;

                    if (buffer.size() >= batchSize) {
                        repaymentRepository.saveAll(buffer);
                        buffer.clear();
                        template.convertAndSend("/topic/progress", "✅ Diproses " + total + " baris...");
                    }
                }

                if (!buffer.isEmpty()) {
                    repaymentRepository.saveAll(buffer);
                    template.convertAndSend("/topic/progress", "✅ Diproses " + total + " baris...");
                }

                template.convertAndSend("/topic/progress", "✅ Proses selesai, total: " + total + " baris");

            } catch (Exception e) {
                e.printStackTrace();
                template.convertAndSend("/topic/progress", "❌ Error: " + e.getMessage());
            }
        }).start();
    }

    public void processTagihan(MultipartFile file) {
        repaymentRepository.deleteAll();

        new Thread(() -> {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
                    CSVParser parser = CSVFormat.DEFAULT.withSkipHeaderRecord(false).parse(reader)
            ) {
                List<Bills> buffer = new ArrayList<>();
                int batchSize = 200;
                int total = 0;

                for (CSVRecord record : parser) {
                    if (record.size() < 27) continue; // jaga-jaga jumlah kolom tidak lengkap

                    Bills bills = Bills.builder()
                            .customerId(record.get(0))
                            .wilayah(record.get(1))
                            .branch(record.get(2))
                            .noSpk(record.get(3))
                            .officeLocation(record.get(4))
                            .product(record.get(5))
                            .name(record.get(6))
                            .address(record.get(7))
                            .payDown(record.get(8))
                            .realization(record.get(9))
                            .dueDate(record.get(10))
                            .collectStatus(record.get(11))
                            .dayLate(record.get(12))
                            .plafond(parseLong(record.get(13)))
                            .debitTray(parseLong(record.get(14)))
                            .interest(parseLong(record.get(15)))
                            .principal(parseLong(record.get(16)))
                            .installment(parseLong(record.get(17)))
                            .lastInterest(parseLong(record.get(18)))
                            .lastPrincipal(parseLong(record.get(19)))
                            .lastInstallment(parseLong(record.get(20)))
                            .fullPayment(parseLong(record.get(21)))
                            .minInterest(parseLong(record.get(22)))
                            .minPrincipal(parseLong(record.get(23)))
                            .penaltyInterest(parseLong(record.get(24)))
                            .penaltyPrincipal(parseLong(record.get(25)))
                            .accountOfficer(record.get(26))
                            .build();

                    buffer.add(bills);
                    total++;

                    // setiap batchSize, simpan dan reset buffer
                    if (buffer.size() >= batchSize) {
                        billsRepository.saveAll(buffer);
                        buffer.clear();
                        template.convertAndSend("/topic/progress", "✅ Diproses " + total + " baris...");
                    }
                }

                // simpan sisa data
                if (!buffer.isEmpty()) {
                    billsRepository.saveAll(buffer);
                    template.convertAndSend("/topic/progress", "✅ Diproses " + total + " baris...");
                }

                template.convertAndSend("/topic/progress", "✅ Proses selesai, total: " + total + " baris");

            } catch (Exception e) {
                e.printStackTrace();
                template.convertAndSend("/topic/progress", "❌ Error: " + e.getMessage());
            }
        }).start();
    }

}