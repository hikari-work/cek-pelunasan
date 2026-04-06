package org.cekpelunasan.core.service.savings;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.repository.SavingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;

import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

/**
 * Mengelola data rekening tabungan nasabah. Class ini merupakan jembatan
 * antara data tabungan yang diimpor dari CSV dengan berbagai kebutuhan pencarian
 * di bot: mulai dari mencari nasabah berdasarkan nama, CIF, nomor rekening,
 * sampai mencari calon nasabah berdasarkan kata kunci alamat.
 *
 * <p>Fitur pencarian berdasarkan alamat secara otomatis menyaring nasabah yang
 * sudah punya tagihan aktif (ada di tabel Bills), sehingga hasilnya lebih
 * relevan untuk prospekting nasabah baru.</p>
 */
@Service
@RequiredArgsConstructor
public class SavingsService {

	private static final Logger log = LoggerFactory.getLogger(SavingsService.class);
	private final SavingsRepository savingsRepository;
	private final ReactiveMongoTemplate mongoTemplate;

	/**
	 * Menyimpan daftar rekening tabungan ke database secara batch. Berguna
	 * untuk proses impor yang memecah data CSV menjadi potongan-potongan kecil.
	 *
	 * @param savingsList daftar rekening tabungan yang ingin disimpan
	 * @return {@link Mono} yang selesai ketika semua data berhasil disimpan
	 */
	public Mono<Void> batchSavingAccounts(@NonNull List<Savings> savingsList) {
		return savingsRepository.saveAll(savingsList).then();
	}

	/**
	 * Mencari rekening tabungan berdasarkan nama nasabah (tidak case-sensitive)
	 * dan kode cabang, dengan hasil 5 data per halaman.
	 *
	 * @param name   sebagian atau seluruh nama nasabah yang ingin dicari
	 * @param branch kode cabang tempat rekening terdaftar
	 * @param page   nomor halaman hasil pencarian (dimulai dari 0)
	 * @return {@link Mono} berisi halaman data rekening yang sesuai
	 */
	public Mono<Page<Savings>> findByNameAndBranch(String name, String branch, int page) {
		Pageable pageable = PageRequest.of(page, 5);
		Mono<List<Savings>> content = savingsRepository
			.findByNameContainingIgnoreCaseAndBranch(name, branch, pageable).collectList();
		Mono<Long> total = savingsRepository
			.countByNameContainingIgnoreCaseAndBranch(name, branch).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
	}

	/**
	 * Mencari rekening tabungan berdasarkan nomor CIF (Customer Identification File).
	 *
	 * @param cif nomor CIF nasabah yang ingin dicari
	 * @return {@link Mono} berisi data rekening, atau kosong jika tidak ditemukan
	 */
	public Mono<Savings> findByCif(String cif) {
		return savingsRepository.findByCif(cif);
	}

	/**
	 * Membaca file CSV tabungan, menghapus data lama, lalu menyimpan data baru
	 * secara batch (500 baris sekaligus). Baris header di CSV dilewati secara
	 * otomatis. Baris dengan jumlah kolom kurang dari 6 juga dilewati karena
	 * dianggap data tidak lengkap.
	 *
	 * <p>Setelah proses selesai, jumlah total data yang tersimpan di database
	 * akan dicatat ke log untuk keperluan verifikasi.</p>
	 *
	 * @param path lokasi file CSV yang berisi data rekening tabungan
	 * @return {@link Mono} yang selesai ketika seluruh data berhasil diimpor
	 */
	public Mono<Void> parseCsvAndSaveIntoDatabase(Path path) {
		return savingsRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						String[] header = reader.readNext(); // skip header row
						if (header == null) { sink.complete(); return; }
						log.info("CSV header: {} kolom", header.length);
						long[] counts = {0, 0}; // [read, skipped]
						String[] line;
						while ((line = reader.readNext()) != null) {
							counts[0]++;
							if (line.length < 6) {
								counts[1]++;
								continue;
							}
							sink.next(line);
						}
						log.info("CSV dibaca: {} baris, dilewati (kolom kurang): {}", counts[0], counts[1]);
						sink.complete();
					} catch (Exception e) {
						log.error("Error parsing CSV file: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToSavings)
				.buffer(500)
				.flatMap(batch -> batchSavingAccounts(batch)
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc())
			.then(savingsRepository.count()
				.doOnNext(total -> log.info("Total savings tersimpan di DB: {}", total))
				.then());
	}

	/**
	 * Menghapus seluruh data rekening tabungan dari database.
	 *
	 * @return {@link Mono} yang selesai ketika penghapusan berhasil
	 */
	public Mono<Void> deleteAll() {
		return savingsRepository.deleteAll();
	}

	/**
	 * Mengonversi satu baris CSV menjadi objek {@link Savings}. Kolom yang
	 * melebihi panjang baris (opsional) diakses dengan aman menggunakan
	 * metode {@link #get(String[], int)} agar tidak terjadi ArrayIndexOutOfBoundsException.
	 * Urutan kolom: branch, type, cif, tabId, name, address, balance, transaction,
	 * accountOfficer, phone, minimumBalance, blockingBalance.
	 *
	 * @param line satu baris CSV dalam bentuk array string
	 * @return objek {@link Savings} yang sudah terisi
	 */
	public Savings mapToSavings(String[] line) {
		return Savings.builder()
			.branch(line[0])
			.type(line[1])
			.cif(line[2])
			.tabId(line[3])
			.name(line[4])
			.address(line[5])
			.balance(parseBigDecimal(get(line, 6)))
			.transaction(parseBigDecimal(get(line, 7)))
			.accountOfficer(get(line, 8))
			.phone(get(line, 9))
			.minimumBalance(parseBigDecimal(get(line, 10)))
			.blockingBalance(parseBigDecimal(get(line, 11)))
			.build();
	}

	/**
	 * Mengambil elemen array string pada indeks tertentu dengan aman.
	 * Mengembalikan {@code null} jika indeks melebihi panjang array,
	 * sehingga baris CSV dengan kolom yang kurang tidak menyebabkan error.
	 *
	 * @param line  array string yang ingin diakses
	 * @param index indeks kolom yang ingin diambil
	 * @return nilai kolom tersebut, atau {@code null} jika indeks di luar batas
	 */
	private String get(String[] line, int index) {
		return index < line.length ? line[index] : null;
	}

	/**
	 * Mengurai string menjadi nilai {@link BigDecimal}. String kosong, null,
	 * atau bukan angka valid akan menghasilkan {@link BigDecimal#ZERO}
	 * tanpa melempar exception.
	 *
	 * @param value string angka yang ingin diurai
	 * @return nilai BigDecimal-nya, atau BigDecimal.ZERO jika tidak valid
	 */
	private BigDecimal parseBigDecimal(String value) {
		if (value == null || value.isBlank()) return BigDecimal.ZERO;
		try {
			return BigDecimal.valueOf(Long.parseLong(value.trim()));
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	/**
	 * Mengambil daftar kode cabang yang memiliki nasabah dengan nama tertentu,
	 * tanpa duplikat dan sudah diurutkan secara alfabetis. Berguna untuk
	 * membangun menu pilihan cabang saat pencarian nasabah.
	 *
	 * @param name kata kunci nama nasabah sebagai filter
	 * @return {@link Mono} berisi set kode cabang yang unik dan terurut
	 */
	public Mono<Set<String>> listAllBranch(String name) {
		Criteria criteria = Criteria.where("name").regex(name, "i");
		Query query = new Query(criteria);
		return mongoTemplate.findDistinct(query, "branch", Savings.class, String.class)
			.filter(b -> b != null && !b.isBlank())
			.sort()
			.collectList()
			.map(LinkedHashSet::new);
	}

	/**
	 * Mencari rekening tabungan berdasarkan nomor rekening (tab ID).
	 *
	 * @param id nomor rekening tabungan yang ingin dicari
	 * @return {@link Mono} berisi data rekening, atau kosong jika tidak ditemukan
	 */
	public Mono<Savings> findById(String id) {
		log.info("Searching for account with ID: {}", id);
		return savingsRepository.findByTabId(id);
	}

	/**
	 * Mencari rekening tabungan berdasarkan nama nasabah, dengan batas jumlah
	 * hasil yang bisa dikonfigurasi. Berguna untuk fitur pencarian cepat.
	 *
	 * @param name  kata kunci nama nasabah yang ingin dicari
	 * @param limit batas maksimal jumlah hasil yang dikembalikan
	 * @return {@link Flux} berisi rekening-rekening yang namanya cocok
	 */
	public Flux<Savings> findByName(String name, int limit) {
		log.info("Searching savings by name: {} (limit {})", name, limit);
		return savingsRepository.findByNameContainingIgnoreCase(name).take(limit);
	}

	/**
	 * Mencari rekening tabungan berdasarkan kata kunci alamat, sambil
	 * mengecualikan nasabah yang sudah punya tagihan aktif di tabel Bills.
	 * Untuk menghindari duplikasi hasil, hanya satu rekening per CIF yang
	 * ditampilkan (menggunakan aggregasi group by CIF).
	 *
	 * <p>Hasil dikembalikan dengan pagination sesuai {@link Pageable} yang diberikan.</p>
	 *
	 * @param addressKeywords daftar kata kunci alamat (semua harus cocok — kondisi AND)
	 * @param pageable        konfigurasi pagination dan sorting
	 * @return {@link Mono} berisi halaman data rekening yang sesuai filter
	 */
	public Mono<Page<Savings>> findFilteredSavings(List<String> addressKeywords, @NonNull Pageable pageable) {
		log.info("Starting findFilteredSavings with keywords: {} and page: {}", addressKeywords, pageable);

		Mono<List<String>> billsCifsMono = mongoTemplate
			.findDistinct("customerId", Bills.class, String.class)
			.filter(cif -> cif != null && !cif.isBlank())
			.collectList();

		return billsCifsMono.flatMap(billsCifs -> {
			Criteria addressCriteria = buildAddressCriteria(addressKeywords);
			Criteria notInBills = Criteria.where("cif").nin(billsCifs);
			Criteria combined = new Criteria().andOperator(addressCriteria, notInBills);

			AggregationOperation match = Aggregation.match(combined);
			AggregationOperation group = Aggregation.group("cif").first("$$ROOT").as("doc");
			AggregationOperation replaceRoot = Aggregation.replaceRoot("doc");
			AggregationOperation skip = Aggregation.skip(pageable.getOffset());
			AggregationOperation limit = Aggregation.limit(pageable.getPageSize());

			Mono<List<Savings>> resultsMono = mongoTemplate.aggregate(
				Aggregation.newAggregation(match, group, replaceRoot, skip, limit),
				Savings.class, Savings.class
			).collectList();

			AggregationOperation countGroup = Aggregation.group("cif");
			AggregationOperation countAll = Aggregation.count().as("total");
			Mono<Long> countMono = mongoTemplate.aggregate(
				Aggregation.newAggregation(match, countGroup, countAll),
				Savings.class, org.bson.Document.class
			).collectList()
				.map(results -> {
					if (results == null || results.isEmpty()) return 0L;
					return ((Number) results.getFirst().get("total")).longValue();
				});

			return Mono.zip(resultsMono, countMono)
				.map(t -> {
					log.info("Query completed successfully. Found {} unique CIFs matching keywords.", t.getT2());
					return (Page<Savings>) new PageImpl<>(t.getT1(), pageable, t.getT2());
				});
		});
	}

	/**
	 * Membangun kriteria MongoDB untuk pencarian berbasis kata kunci alamat.
	 * Setiap kata kunci dijadikan kondisi regex case-insensitive, dan semua
	 * kondisi digabungkan dengan AND. Jika daftar kata kunci kosong atau null,
	 * dikembalikan kriteria kosong (tidak ada filter).
	 *
	 * @param addressKeywords daftar kata kunci yang ingin dijadikan filter alamat
	 * @return objek {@link Criteria} yang siap digunakan dalam query MongoDB
	 */
	private Criteria buildAddressCriteria(List<String> addressKeywords) {
		if (addressKeywords == null || addressKeywords.isEmpty()) {
			return new Criteria();
		}
		List<Criteria> predicates = addressKeywords.stream()
			.filter(k -> k != null && !k.trim().isEmpty())
			.map(k -> Criteria.where("address").regex(k.trim(), "i"))
			.toList();
		if (predicates.isEmpty()) {
			return new Criteria();
		}
		return new Criteria().andOperator(predicates.toArray(new Criteria[0]));
	}
}
