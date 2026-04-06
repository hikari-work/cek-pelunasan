package org.cekpelunasan.core.service.kolektas;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.KolekTas;
import org.cekpelunasan.core.repository.KolekTasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KolekTasService {

	private static final Logger log = LoggerFactory.getLogger(KolekTasService.class);
	private final KolekTasRepository kolekTasRepository;

	public Mono<Page<KolekTas>> findKolekByKelompok(String kelompok, int page, int size) {
		int zeroBasedPage = page > 0 ? page - 1 : 0;
		PageRequest pageRequest = PageRequest.of(zeroBasedPage, size);
		Mono<List<KolekTas>> content = kolekTasRepository.findByKelompokIgnoreCase(kelompok, pageRequest).collectList();
		Mono<Long> total = kolekTasRepository.countByKelompokIgnoreCase(kelompok).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	public Mono<Void> saveAll(@NonNull List<KolekTas> kolekTas) {
		return kolekTasRepository.saveAll(kolekTas).then();
	}

	public Mono<Void> deleteAll() {
		return kolekTasRepository.deleteAll();
	}

	public Mono<Void> parseCsvAndSave(Path path) {
		return kolekTasRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						reader.readNext(); // skip header
						String[] line;
						while ((line = reader.readNext()) != null) {
							sink.next(line);
						}
						sink.complete();
					} catch (Exception e) {
						log.error("Gagal membaca file CSV: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToKolekTas)
				.buffer(500)
				.flatMap(batch -> saveAll(batch)
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc());
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
