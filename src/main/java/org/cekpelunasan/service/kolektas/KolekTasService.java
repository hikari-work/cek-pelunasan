package org.cekpelunasan.service.kolektas;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.entity.KolekTas;
import org.cekpelunasan.repository.KolekTasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import lombok.NonNull;

@Service
@RequiredArgsConstructor
public class KolekTasService {

	private static final Logger log = LoggerFactory.getLogger(KolekTasService.class);
	private final KolekTasRepository kolekTasRepository;

	public Page<KolekTas> findKolekByKelompok(String kelompok, int page, int size) {
		int zeroBasedPage = page > 0 ? page - 1 : 0;
		return kolekTasRepository.findByKelompokIgnoreCase(kelompok, PageRequest.of(zeroBasedPage, size));
	}

	public void saveAll(@NonNull List<KolekTas> kolekTas) {
		kolekTasRepository.saveAll(kolekTas);
	}

	@Transactional
	public void deleteAll() {
		kolekTasRepository.deleteAllFast();
	}

	public void parseCsvAndSave(Path path) {
		final int BATCH_SIZE = 500;
		final int MAX_CONCURRENT_TASK = 15;
		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASK);
		List<KolekTas> currentBatch = new ArrayList<>(BATCH_SIZE);
		int counter = 0;
		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			reader.readNext();
			while ((line = reader.readNext()) != null) {
				currentBatch.add(mapToKolekTas(line));
				if (currentBatch.size() >= BATCH_SIZE) {
					List<KolekTas> batcToSave = new ArrayList<>(currentBatch);
					semaphore.acquire();
					executorService.submit(() -> {
						try {
							saveAll(batcToSave);
						} catch (Exception e) {
							log.error("Error Saving Batch");
						} finally {
							semaphore.release();
						}
					});
					counter += BATCH_SIZE;
					System.out.println("Submitted Batch" + counter);
					currentBatch.clear();
				}
			}
			if (!currentBatch.isEmpty()) {
				List<KolekTas> batchLast = new ArrayList<>(currentBatch);
				semaphore.acquire();
				executorService.submit(() -> {
					try {
						saveAll(batchLast);
					} catch (Exception e) {
						log.info("Error Saving last Batch");
					} finally {
						semaphore.release();
					}
					System.out.println("Saved All In Database");
				});
			}
			executorService.shutdown();
		} catch (Exception e) {
			log.error("Gagal membaca file CSV: {}", e.getMessage(), e);
		}
	}

	public KolekTas mapToKolekTas(String[] line) {
		return KolekTas.builder()
				.kelompok(line[0])
				.kantor(line[1])
				.rekening(line[2])
				.nama(line[3])
				.alamat(line[4])
				.noHp(line[5])
				.kolek(line[6])
				.nominal(formatRupiah(Long.parseLong(line[7])))
				.accountOfficer(line[8])
				.cif(line[9])
				.build();
	}

	public String formatRupiah(Long amount) {
		if (amount == null)
			return "Rp0";
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		symbols.setDecimalSeparator(',');
		DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
		return df.format(amount);
	}
}