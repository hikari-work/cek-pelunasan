package org.cekpelunasan.service;

import com.opencsv.CSVReader;
import org.cekpelunasan.entity.Simulasi;
import org.cekpelunasan.entity.SimulasiResult;
import org.cekpelunasan.repository.SimulasiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
public class SimulasiService {

	private static final Logger log = LoggerFactory.getLogger(SimulasiService.class);
	private final TransactionTemplate transactionTemplate;
	private final SimulasiRepository simulasiRepository;

	public SimulasiService(TransactionTemplate transactionTemplate, SimulasiRepository simulasiRepository1) {
		this.transactionTemplate = transactionTemplate;
		this.simulasiRepository = simulasiRepository1;
	}

	public SimulasiResult getSimulasi(String spk, Long userInput) {
		return transactionTemplate.execute(status -> {
			long remaining = userInput;
			long masukPokok = 0;
			long masukBunga = 0;
			List<Simulasi> simulasis = simulasiRepository.findBySpkOrderByKeterlambatanDesc(spk);
			log.info("Simulasi menemukan {}", simulasis.size());
			log.info("SPK adalah {}", spk);
			if (simulasis.isEmpty()) {
				return null;
			}
			Long maxdate = simulasis.getFirst().getKeterlambatan();
			log.info("Keterlambatan {} Hari", maxdate);
			List<Simulasi> pokok = simulasis.stream()
				.filter(d -> d.getSequence().equals("P"))
				.toList();
			log.info("Masuk Bunga Berapa?");
			List<Simulasi> bunga = simulasis.stream()
				.filter(d -> d.getSequence().equals("I"))
				.toList();
			log.info("Masuk Pokok Berapa?");
			List<Simulasi> targetFirst = (maxdate > 90) ? pokok : bunga;
			List<Simulasi> secondTarget = (maxdate > 90) ? bunga : pokok;
			long[] resultFirst = kurangiTunggakan(targetFirst, remaining);
			log.info("Isinya Apa aja {}", resultFirst);
			remaining = resultFirst[0];
			if (maxdate > 90) masukBunga =+ resultFirst[1]; else masukBunga =+ resultFirst[1];
			if (remaining > 0) {
				long[] resultSecond = kurangiTunggakan(secondTarget, remaining);
				remaining = resultSecond[0];
				if (maxdate > 90) masukBunga += resultSecond[1]; else masukPokok += resultSecond[1];
			}
			long akhir = simulasiRepository.findBySpkOrderByKeterlambatanDesc(spk)
				.stream()
				.mapToLong(Simulasi::getKeterlambatan)
				.max()
				.orElse(0L);
			status.setRollbackOnly();
			return new SimulasiResult(masukPokok, masukBunga, akhir);
		});
	}

	private long[] kurangiTunggakan(List<Simulasi> simulasis, Long amount) {
		long totalMasuk = 0;
		for (Simulasi simulasi : simulasis) {
			if (amount <= 0) break;
			long currentTunggakan = simulasi.getTunggakan();
			long kurangi = Math.min(currentTunggakan, amount);
			simulasi.setTunggakan(currentTunggakan - kurangi);
			simulasiRepository.save(simulasi);
			amount -= kurangi;
			totalMasuk += kurangi;
		}
		return new long[]{totalMasuk, amount};
	}
	public void parseCsv(Path path) {
		simulasiRepository.deleteAll();
		final int BATCH_SIZE = 500;
		final int MAX_CONCURRENT_TASK = 10;
		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASK);
		List<Simulasi> currentBranch = new ArrayList<>(BATCH_SIZE);
		try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
			String[] line;
			while ((line = reader.readNext()) != null) {
				currentBranch.add(mapToSimulasi(line));
				if (currentBranch.size() >= BATCH_SIZE) {
					List<Simulasi> simulasiToSave = new ArrayList<>(currentBranch);
					semaphore.acquire();
					executorService.submit(() -> {
						try {
							simulasiRepository.saveAll(simulasiToSave);
						} catch (Exception e) {
							log.error("Error Saving");
						} finally {
							semaphore.release();
						}
					});
					currentBranch.clear();
				}
			}
			if (!currentBranch.isEmpty()) {
				List<Simulasi> simulasiToSave = new ArrayList<>(currentBranch);
				semaphore.acquire();
				executorService.submit(() -> {
					try {
						simulasiRepository.saveAll(simulasiToSave);
					} catch (Exception e) {
						log.error("Error Saving");
					} finally {
						semaphore.release();
					}
				});
				currentBranch.clear();
			}

		} catch (Exception e) {
			log.error("Gagal membaca file CSV: {}", e.getMessage(), e);
		}
	}
	public Simulasi mapToSimulasi(String[] lines) {
		return Simulasi.builder()
			.spk(lines[0])
			.tanggal(lines[1])
			.sequence(lines[2])
			.tunggakan(Long.parseLong(lines[3]))
			.denda(Long.parseLong(lines[4]))
			.keterlambatan(Long.parseLong(lines[5]))
			.build();
	}


}



