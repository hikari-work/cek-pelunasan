package org.cekpelunasan.core.service.bill;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.repository.BillsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import org.cekpelunasan.core.service.log.DataUpdateLogService;

import java.time.Duration;

import java.io.FileReader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pusat kendali data tagihan (bills) nasabah. Class ini menangani segala
 * kebutuhan seputar data pelunasan: mulai dari membaca file CSV yang
 * diunggah admin, menyimpan ribuan baris ke MongoDB, sampai menyajikan
 * data terfilter ke pengguna bot.
 *
 * <p>Semua operasi bersifat reaktif (non-blocking) menggunakan Project Reactor,
 * jadi server tidak akan macet meskipun sedang memproses file besar.</p>
 */
@Service
@RequiredArgsConstructor
public class BillService {

	private static final Logger log = LoggerFactory.getLogger(BillService.class);
	private final BillsRepository billsRepository;
	private final ReactiveMongoTemplate mongoTemplate;
	private final DataUpdateLogService dataUpdateLogService;

	/**
	 * Mengambil daftar semua kode cabang yang ada di database, tanpa duplikat.
	 * Hasilnya disimpan dalam {@link LinkedHashSet} agar urutannya konsisten.
	 *
	 * @return {@link Mono} berisi set nama-nama cabang yang unik
	 */
	public Mono<Set<String>> lisAllBranch() {
		return mongoTemplate.findDistinct("branch", Bills.class, String.class)
			.collectList()
			.map(list -> new LinkedHashSet<>(Objects.requireNonNullElse(list, List.of())));
	}

	/**
	 * Mencari satu data tagihan berdasarkan ID dokumennya di MongoDB.
	 *
	 * @param id ID dokumen tagihan yang ingin dicari
	 * @return {@link Mono} berisi data tagihan, atau kosong kalau tidak ditemukan
	 */
	public Mono<Bills> getBillById(@NonNull String id) {
		return billsRepository.findById(id);
	}

	/**
	 * Menghitung total seluruh tagihan yang tersimpan di database.
	 * Berguna untuk dashboard atau validasi setelah impor CSV.
	 *
	 * @return {@link Mono} berisi jumlah total dokumen tagihan
	 */
	public Mono<Long> countAllBills() {
		return billsRepository.count();
	}

	/**
	 * Mengambil semua tagihan yang dimiliki oleh satu cabang tertentu.
	 *
	 * @param branch kode atau nama cabang yang ingin dicari
	 * @return {@link Mono} berisi list tagihan dari cabang tersebut
	 */
	public Mono<List<Bills>> findAllBillsByBranch(String branch) {
		return billsRepository.findAllByBranch(branch).collectList();
	}

	/**
	 * Mengambil tagihan berdasarkan nama Account Officer dan status bayar,
	 * dengan hasil yang dibagi per halaman (pagination).
	 *
	 * @param accountOfficer nama AO yang menjadi penanggung jawab tagihan
	 * @param payDown        status pembayaran yang ingin difilter
	 * @param page           nomor halaman (dimulai dari 0)
	 * @param size           jumlah data per halaman
	 * @return {@link Mono} berisi halaman data tagihan yang sesuai filter
	 */
	public Mono<Page<Bills>> findDueDateByAccountOfficer(String accountOfficer, String payDown, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByAccountOfficerAndPayDown(accountOfficer, payDown, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByAccountOfficerAndPayDown(accountOfficer, payDown).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	/**
	 * Mengambil tagihan berdasarkan cabang dan status bayar, diurutkan
	 * berdasarkan nama Account Officer, dengan pagination.
	 *
	 * @param branch  kode cabang yang ingin difilter
	 * @param payDown status pembayaran yang ingin difilter
	 * @param page    nomor halaman (dimulai dari 0)
	 * @param size    jumlah data per halaman
	 * @return {@link Mono} berisi halaman data tagihan yang sesuai filter
	 */
	public Mono<Page<Bills>> findBranchAndPayDown(String branch, String payDown, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByBranchAndPayDownOrderByAccountOfficer(branch, payDown, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByBranchAndPayDown(branch, payDown).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	/**
	 * Mencari tagihan berdasarkan nama nasabah (tidak case-sensitive) dan cabang,
	 * dengan pagination. Cocok untuk fitur pencarian nasabah di bot.
	 *
	 * @param name   sebagian atau seluruh nama nasabah yang ingin dicari
	 * @param branch kode cabang tempat nasabah terdaftar
	 * @param page   nomor halaman (dimulai dari 0)
	 * @param size   jumlah data per halaman
	 * @return {@link Mono} berisi halaman hasil pencarian
	 */
	public Mono<Page<Bills>> findByNameAndBranch(String name, String branch, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByNameContainingIgnoreCaseAndBranch(name, branch, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByNameContainingIgnoreCaseAndBranch(name, branch).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	/**
	 * Mengambil tagihan dengan pembayaran minimal (kios tertentu), yaitu tagihan
	 * yang nilai {@code totalMin}-nya lebih dari nol, dengan pagination.
	 *
	 * @param branch kode cabang yang ingin dicari
	 * @param page   nomor halaman (dimulai dari 0)
	 * @param size   jumlah data per halaman
	 * @return {@link Mono} berisi halaman tagihan dengan minimal bayar
	 */
	public Mono<Page<Bills>> findMinimalPaymentByBranch(String branch, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		return billsRepository.findByKiosAndTotalMin(branch, 0L, pageRequest).collectList()
			.map(content -> new PageImpl<>(content, pageRequest, 0L));
	}

	/**
	 * Mengambil tagihan milik satu Account Officer yang masih punya tagihan
	 * bunga atau pokok minimal, dengan pagination.
	 *
	 * @param officer nama AO yang ingin dicari datanya
	 * @param page    nomor halaman (dimulai dari 0)
	 * @param size    jumlah data per halaman
	 * @return {@link Mono} berisi halaman tagihan minimal dari AO tersebut
	 */
	public Mono<Page<Bills>> findMinimalPaymentByAccountOfficer(String officer, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(0L, 0L, officer, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(0L, 0L, officer).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	/**
	 * Mengambil daftar semua nama Account Officer yang unik dari seluruh data
	 * tagihan. Berguna untuk membuat menu pilihan AO di bot.
	 *
	 * @return {@link Mono} berisi set nama-nama AO yang unik
	 */
	public Mono<Set<String>> findAllAccountOfficer() {
		return mongoTemplate.findDistinct("accountOfficer", Bills.class, String.class)
			.collectList()
			.map(list -> new LinkedHashSet<>(Objects.requireNonNullElse(list, List.of())));
	}

	/**
	 * Membaca file CSV, menghapus seluruh data lama, lalu menyimpan data baru
	 * ke MongoDB secara batch (500 baris sekaligus). Proses ini dilakukan di
	 * thread terpisah supaya tidak memblokir thread utama.
	 *
	 * <p>Jika ada kegagalan saat menyimpan, sistem akan mencoba ulang sampai
	 * 3 kali dengan jeda 1 detik antar percobaan. Setelah selesai, GC dipanggil
	 * untuk membersihkan memori dari sisa-sisa buffer CSV.</p>
	 *
	 * @param path lokasi file CSV yang ingin diimpor
	 * @return {@link Mono} yang selesai ketika seluruh data berhasil disimpan
	 */
	public Mono<Void> parseCsvAndSaveIntoDatabase(Path path) {
		return billsRepository.deleteAll()
			.then(Flux.<String[]>create(sink -> {
					try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
						String[] line;
						while ((line = reader.readNext()) != null) {
							sink.next(line);
						}
						sink.complete();
					} catch (Exception e) {
						log.error("Error reading CSV: {}", e.getMessage(), e);
						sink.error(e);
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::mapToBill)
				.buffer(500)
				.flatMap(batch -> billsRepository.saveAll(batch).then()
					.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))),
					Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc())
			.then(dataUpdateLogService.saveUpdateTimestamp("TAGIHAN"));
	}

	/**
	 * Mencari tagihan berdasarkan nama nasabah lintas semua cabang (tanpa filter cabang),
	 * dengan pagination. Dipakai oleh Mini App untuk pencarian cross-branch.
	 *
	 * @param name nama nasabah yang ingin dicari (parsial, tidak case-sensitive)
	 * @param page nomor halaman (dimulai dari 0)
	 * @param size jumlah data per halaman
	 * @return {@link Mono} berisi halaman hasil pencarian
	 */
	public Mono<Page<Bills>> findByName(String name, int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Mono<List<Bills>> content = billsRepository
			.findByNameContainingIgnoreCase(name, pageRequest).collectList();
		Mono<Long> total = billsRepository
			.countByNameContainingIgnoreCase(name).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	/**
	 * Menghapus seluruh data tagihan dari database. Biasanya dipanggil sebelum
	 * impor ulang data CSV yang baru.
	 *
	 * @return {@link Mono} yang selesai ketika penghapusan berhasil
	 */
	public Mono<Void> deleteAll() {
		return billsRepository.deleteAll();
	}

	/**
	 * Mengonversi satu baris CSV (array string) menjadi objek {@link Bills}.
	 * Urutan kolom di sini harus persis sama dengan format file CSV yang diterima.
	 * Nilai numerik yang kosong atau tidak valid otomatis diisi dengan 0.
	 *
	 * @param line satu baris CSV dalam bentuk array string
	 * @return objek {@link Bills} yang sudah terisi lengkap
	 */
	private Bills mapToBill(String[] line) {
		return Bills.builder()
			.customerId(line[0])
			.wilayah(line[1])
			.branch(line[2])
			.noSpk(line[3])
			.officeLocation(line[4])
			.product(line[5])
			.name(line[6])
			.address(line[7])
			.payDown(line[8])
			.realization(line[9])
			.dueDate(line[10])
			.collectStatus(line[11])
			.dayLate(line[12])
			.plafond(parseLong(line[13]))
			.debitTray(parseLong(line[14]))
			.interest(parseLong(line[15]))
			.principal(parseLong(line[16]))
			.installment(parseLong(line[17]))
			.lastInterest(parseLong(line[18]))
			.lastPrincipal(parseLong(line[19]))
			.lastInstallment(parseLong(line[20]))
			.fullPayment(parseLong(line[21]))
			.minInterest(parseLong(line[22]))
			.minPrincipal(parseLong(line[23]))
			.penaltyInterest(parseLong(line[24]))
			.penaltyPrincipal(parseLong(line[25]))
			.accountOfficer(line[26])
			.kios(line[28])
			.titipan(parseLong(line[29]))
			.fixedInterest(parseLong(line[30]))
			.build();
	}

	/**
	 * Mengurai string menjadi nilai {@code long}. Kalau string-nya kosong,
	 * mengandung spasi, atau bukan angka, hasilnya adalah 0 tanpa melempar
	 * exception — jadi proses impor tidak akan berhenti karena satu baris bermasalah.
	 *
	 * @param value string angka yang ingin diurai
	 * @return nilai long-nya, atau 0 kalau tidak valid
	 */
	private long parseLong(String value) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
