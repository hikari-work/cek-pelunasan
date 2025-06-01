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

@Service
public class SimulasiService {

	private static final Logger log = LoggerFactory.getLogger(SimulasiService.class);

	private final SimulasiRepository simulasiRepository;

	public SimulasiService(SimulasiRepository simulasiRepository1) {
		this.simulasiRepository = simulasiRepository1;
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
		long terlambatBesar = keterlambatan.stream().filter(t -> t.getTunggakan() > 0).mapToLong(Simulasi::getKeterlambatan).max().orElse(0L);
		AtomicLong sisaSaldo = new AtomicLong(userInput);

		long pokok = 0L;
		long bunga = 0L;
		AtomicLong masukPokok = new AtomicLong(pokok);
		AtomicLong masukBunga = new AtomicLong(bunga);
		log.info("Terlambat besar adalah {}, yang mana akan menghasilkan {}", terlambatBesar, terlambatBesar <=90);

		if (terlambatBesar <= 90) {
			keterlambatan.stream()
				.filter(data -> data.getSequence().equals("I"))
				.forEach(kurangi -> {
					long tunggakan = kurangi.getTunggakan();
					long saldoSekarang = sisaSaldo.get();

					if (tunggakan == 0 || saldoSekarang == 0) return;

					if (saldoSekarang >= tunggakan) {
						kurangi.setTunggakan(0L);
						kurangi.setKeterlambatan(0L);
						sisaSaldo.addAndGet(-tunggakan);
						masukBunga.addAndGet(tunggakan);
					} else {
						kurangi.setTunggakan(tunggakan - saldoSekarang);
						masukBunga.addAndGet(saldoSekarang);
						sisaSaldo.set(0);
					}
				});

			if (sisaSaldo.get() > 0) {
				keterlambatan.stream()
					.filter(data -> data.getSequence().equals("P"))
					.forEach(kurangi -> {
						long tunggakan = kurangi.getTunggakan();
						long saldoSekarang = sisaSaldo.get();

						if (tunggakan == 0 || saldoSekarang == 0) return;

						if (saldoSekarang >= tunggakan) {
							kurangi.setTunggakan(0L);
							masukPokok.addAndGet(tunggakan);
							sisaSaldo.addAndGet(-tunggakan);
						} else {
							kurangi.setTunggakan(tunggakan - saldoSekarang);
							masukPokok.addAndGet(saldoSekarang);
							sisaSaldo.set(0);
						}
					});
			}
		} else {
			keterlambatan.stream()
				.filter(data -> data.getSequence().equals("P"))
				.forEach(kurangi -> {
					long tunggakan = kurangi.getTunggakan();
					long saldoSekarang = sisaSaldo.get();

					if (tunggakan == 0 || saldoSekarang == 0) return;

					if (saldoSekarang >= tunggakan) {
						kurangi.setTunggakan(0L);
						kurangi.setKeterlambatan(0L);
						masukPokok.addAndGet(tunggakan);
						sisaSaldo.addAndGet(-tunggakan);
					} else {
						kurangi.setTunggakan(tunggakan - saldoSekarang);
						masukPokok.addAndGet(saldoSekarang);
						sisaSaldo.set(0);
					}
				});
			if (sisaSaldo.get() > 0) {
				keterlambatan.stream()
					.filter(data -> data.getSequence().equals("I"))
					.forEach(kurangi -> {
						long tunggakan = kurangi.getTunggakan();
						long saldoSekarang = sisaSaldo.get();

						if (tunggakan == 0 || saldoSekarang == 0) return;

						if (saldoSekarang >= tunggakan) {
							kurangi.setTunggakan(0L);
							masukBunga.addAndGet(tunggakan);
							sisaSaldo.addAndGet(-tunggakan);
						} else {
							kurangi.setTunggakan(tunggakan - saldoSekarang);
							masukBunga.addAndGet(saldoSekarang);
							sisaSaldo.set(0);
						}
					});
			}
		}
		long dayOfLate = keterlambatan.stream()
			.filter(data -> data.getTunggakan() > 0L)
			.mapToLong(Simulasi::getKeterlambatan)
			.max()
			.orElse(0L);
		log.info("Keterlambatan terbesar adalah {} hari", dayOfLate);
		return SimulasiResult.builder()
			.masukI(masukBunga.get())
			.masukP(masukPokok.get())
			.maxDate(dayOfLate + lastDay())
			.build();
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
	private long lastDay() {
		return ChronoUnit.DAYS.between(LocalDate.now(), YearMonth.now().atEndOfMonth());
	}

}



