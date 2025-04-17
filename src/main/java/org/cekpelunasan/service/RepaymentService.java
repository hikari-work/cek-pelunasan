package org.cekpelunasan.service;

import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.repository.RepaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class RepaymentService {

    private final RepaymentRepository repaymentRepository;

    public RepaymentService(RepaymentRepository repaymentRepository) {
        this.repaymentRepository = repaymentRepository;
    }

    public void saveRepayment(Repayment repayment) {
        repaymentRepository.save(repayment);
    }

    public Repayment findRepaymentById(Long id) {
        return repaymentRepository.findById(id).orElse(null);
    }

    public void parseCsvAndSaveIntoDatabase(Path path){
        List<Repayment> repayments = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
            String[] line;
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
                saveRepayment(repayment);
            }

            repaymentRepository.deleteAll(); // bisa juga pakai custom delete by condition kalau tidak mau hapus semua
            repaymentRepository.saveAll(repayments); // simpan sekaligus
            log.info("✅ Upload selesai: {} data disimpan", repayments.size());

        } catch (Exception e) {
            log.warn("❌ Gagal upload CSV: {}", e.getMessage());
        }


    }
    public Repayment findAll() {
        return repaymentRepository.findAll().getFirst();
    }
    public Page<Repayment> findName(String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repaymentRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public Long countAll() {
        return repaymentRepository.count();
    }
}
