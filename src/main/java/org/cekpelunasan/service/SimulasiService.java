package org.cekpelunasan.service;

import org.cekpelunasan.entity.Simulasi;
import org.cekpelunasan.entity.SimulasiResult;
import org.cekpelunasan.repository.SimulasiRepository;
import org.springframework.boot.actuate.metrics.data.MetricsRepositoryMethodInvocationListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class SimulasiService {

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
			List<Simulasi> simulasis = simulasiRepository.findBySpkAndTunggakanGreaterThanOrderByKeterlambatanDesc(spk, Integer.parseInt(userInput.toString()));
			if (simulasis.isEmpty()) {
				return null;
			}
			Long maxdate = simulasis.getFirst().getKeterlambatan();
			List<Simulasi> pokok = simulasis.stream()
				.filter(d -> d.getSequence().equals("P"))
				.toList();
			List<Simulasi> bunga = simulasis.stream()
				.filter(d -> d.getSequence().equals("I"))
				.toList();
			List<Simulasi> targetFirst = (maxdate > 90) ? pokok : bunga;
			List<Simulasi> secondTarget = (maxdate > 90) ? bunga : pokok;
			long[] resultFirst = kurangiTunggakan(targetFirst, remaining);

			remaining = resultFirst[0];
			if (maxdate > 90) masukBunga =+ resultFirst[1]; else masukBunga =+ resultFirst[1];
			if (remaining > 0) {
				long[] resultSecond = kurangiTunggakan(secondTarget, remaining);
				remaining = resultSecond[0];
				if (maxdate > 90) masukBunga += resultSecond[1]; else masukPokok += resultSecond[1];
			}
			long akhir = simulasiRepository.findBySpkAndTunggakanGreaterThanOrderByKeterlambatanDesc(spk, Integer.parseInt(userInput.toString()))
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


}



