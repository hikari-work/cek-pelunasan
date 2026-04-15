package org.cekpelunasan.core.service.bill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.Paying;
import org.cekpelunasan.core.repository.BillsRepository;
import org.cekpelunasan.core.repository.PayingRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import lombok.NonNull;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Mengelola data koleksi khusus untuk cabang "Hot Kolek" (kode cabang 1075).
 * Class ini membantu Account Officer menemukan nasabah mana saja yang perlu
 * ditagih berdasarkan tiga kategori: jatuh tempo bulan ini, pembayaran pertama
 * bulan lalu, dan nasabah dengan pembayaran minimal.
 *
 * <p>Selain itu, class ini juga menyaring nasabah yang sudah melunasi tagihan
 * (sudah ada di tabel {@code Paying}) agar AO tidak perlu membuang waktu
 * menghubungi orang yang sudah bayar.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotKolekService {

	private final PayingRepository payingRepository;
	private final BillsRepository billsRepository;
	private static final String TARGET_BRANCH = "1075";

	/**
	 * Mengambil bulan dan tahun saat ini dalam format {@code yyyy-MM}.
	 * Digunakan untuk memfilter tagihan yang jatuh tempo bulan ini.
	 *
	 * @return string bulan saat ini, contoh: "2025-06"
	 */
	private String getMonth() {
		YearMonth month = YearMonth.now(ZoneOffset.ofHours(7));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
		return formatter.format(month);
	}

	/**
	 * Mengambil bulan lalu dalam format {@code yyyy-MM}.
	 * Digunakan untuk menemukan nasabah yang baru pertama kali bayar bulan lalu.
	 *
	 * @return string bulan lalu, contoh: "2025-05"
	 */
	private String getLastMonth() {
		YearMonth month = YearMonth.now(ZoneOffset.ofHours(7)).minusMonths(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
		return formatter.format(month);
	}

	/**
	 * Mencari tagihan yang jatuh tempo pada bulan ini untuk kios tertentu,
	 * kemudian menyaring tagihan yang sudah dibayar dari hasilnya.
	 *
	 * @param kiosCode kode kios yang ingin dicari, atau kode cabang jika ingin semua
	 * @return {@link Mono} berisi list tagihan yang belum dibayar dan jatuh tempo bulan ini
	 */
	public Mono<List<Bills>> findDueDate(String kiosCode) {
		log.debug("Finding due date bills for kios: '{}', month: '{}'", kiosCode, getMonth());
		return billsRepository.findByBranchAndDueDateContaining(TARGET_BRANCH, getMonth())
			.collectList()
			.flatMap(bills -> filterBillsByKios(bills, kiosCode));
	}

	/**
	 * Mencari nasabah yang baru pertama kali membayar angsuran pada bulan lalu
	 * (berdasarkan tanggal realisasi), untuk kios tertentu. Berguna untuk
	 * memastikan mereka membayar angsuran keduanya tepat waktu.
	 *
	 * @param kiosCode kode kios yang ingin dicari
	 * @return {@link Mono} berisi list tagihan nasabah pembayaran pertama bulan lalu
	 */
	public Mono<List<Bills>> findFirstPay(String kiosCode) {
		log.debug("Finding first pay bills for kios: '{}', lastMonth: '{}'", kiosCode, getLastMonth());
		return billsRepository.findByBranchAndRealizationContaining(TARGET_BRANCH, getLastMonth())
			.collectList()
			.flatMap(bills -> filterBillsByKios(bills, kiosCode));
	}

	/**
	 * Mencari nasabah yang hanya wajib membayar sejumlah minimal (bukan lunas),
	 * untuk kios tertentu. Nasabah yang hari keterlambatannya di atas 125 hari
	 * dikeluarkan dari daftar karena sudah masuk penanganan khusus.
	 *
	 * @param kiosCode kode kios yang ingin dicari
	 * @return {@link Mono} berisi list tagihan nasabah minimal bayar yang valid
	 */
	public Mono<List<Bills>> findMinimalPay(String kiosCode) {
		log.debug("Getting minimal pay bills for kios: '{}'", kiosCode);
		Mono<List<Bills>> billsMono;
		if (TARGET_BRANCH.equals(kiosCode) || kiosCode == null || kiosCode.trim().isEmpty()) {
			billsMono = billsRepository.findByBranchAndTotalMin(TARGET_BRANCH, 0L, Pageable.unpaged()).collectList();
		} else {
			billsMono = billsRepository.findByBranchAndKiosAndTotalMin(TARGET_BRANCH, kiosCode, 0L, Pageable.unpaged()).collectList();
		}
		return billsMono
			.defaultIfEmpty(List.of())
			.flatMap(bills -> filterBillsByKios(bills, kiosCode))
			.map(filtered -> {
				filtered.removeIf(this::isNotValidBills);
				log.debug("Found {} bills after validation", filtered.size());
				return filtered;
			});
	}

	/**
	 * Mengambil semua tagihan dari suatu cabang yang belum dibayar, dengan
	 * cara mengecualikan nomor SPK yang sudah ada di tabel {@code Paying}.
	 *
	 * @param branch kode cabang yang ingin dicari tagihannya
	 * @return {@link Mono} berisi list tagihan yang belum dibayar
	 */
	public Mono<List<Bills>> findUnpaidBillsByBranch(String branch) {
		return payingRepository.findAll()
			.map(Paying::getId)
			.collectList()
			.flatMap(paidIds -> {
				if (paidIds.isEmpty()) {
					return billsRepository.findAllByBranch(branch).collectList();
				}
				return billsRepository.findByBranchAndNoSpkNotIn(branch, paidIds).collectList();
			});
	}

	/**
	 * Menyaring daftar tagihan berdasarkan kode kios dan menghapus tagihan
	 * yang nomor SPK-nya sudah ada di daftar lunas. Logika penyaringan kios:
	 * <ul>
	 *   <li>Kalau kiosCode sama dengan kode cabang (1075), tampilkan tagihan
	 *       yang field kiosnya kosong atau null (artinya milik cabang langsung).</li>
	 *   <li>Kalau kiosCode diisi, tampilkan hanya tagihan milik kios tersebut.</li>
	 *   <li>Selalu hanya tampilkan tagihan dari cabang 1075.</li>
	 * </ul>
	 *
	 * @param bills    list tagihan awal sebelum disaring
	 * @param kiosCode kode kios yang menjadi filter
	 * @return {@link Mono} berisi list tagihan setelah disaring
	 */
	private Mono<List<Bills>> filterBillsByKios(List<Bills> bills, String kiosCode) {
		log.debug("Filtering bills for kios: '{}', input size: {}", kiosCode, bills.size());
		if (bills.isEmpty()) {
			return Mono.just(bills);
		}
		return payingRepository.findAll().map(Paying::getId).collectList()
			.map(paidSpks -> {
				List<Bills> mutableBills = new ArrayList<>(bills);
				int beforePayingFilter = mutableBills.size();
				mutableBills.removeIf(bill -> paidSpks.contains(bill.getNoSpk()));
				log.debug("After paid filter: {} -> {}", beforePayingFilter, mutableBills.size());

				int beforeKiosFilter = mutableBills.size();
				if (TARGET_BRANCH.equals(kiosCode)) {
					mutableBills.removeIf(bill -> {
						String billKios = bill.getKios();
						boolean isEmptyOrNull = billKios == null || billKios.trim().isEmpty();
						log.trace("Bill {} kios: '{}', isEmptyOrNull: {}", bill.getNoSpk(), billKios, isEmptyOrNull);
						return !isEmptyOrNull;
					});
					log.debug("After kios NULL/empty filter (for branch code): {} -> {}", beforeKiosFilter, mutableBills.size());
				} else if (kiosCode != null && !kiosCode.trim().isEmpty()) {
					mutableBills.removeIf(bill -> !kiosCode.equals(bill.getKios()));
					log.debug("After kios '{}' filter: {} -> {}", kiosCode, beforeKiosFilter, mutableBills.size());
				}
				int beforeBranchFilter = mutableBills.size();
				mutableBills.removeIf(bill -> !TARGET_BRANCH.equals(bill.getBranch()));
				log.debug("After branch 1075 filter: {} -> {}", beforeBranchFilter, mutableBills.size());

				return mutableBills;
			});
	}

	/**
	 * Menyimpan daftar nomor SPK ke tabel {@code Paying} sebagai tanda bahwa
	 * tagihan tersebut sudah dibayar. Setiap SPK disimpan dengan flag {@code paid = true}.
	 *
	 * @param list daftar nomor SPK yang ingin ditandai sebagai sudah bayar
	 * @return {@link Mono} yang selesai ketika semua record berhasil disimpan
	 */
	public Mono<Void> saveAllPaying(@NonNull List<String> list) {
		log.debug("Saving {} paying records", list.size());
		return payingRepository.saveAll(list.stream()
			.map(spk -> Paying.builder()
				.id(spk)
				.paid(true)
				.build())
			.toList())
			.then()
			.doOnSuccess(v -> log.debug("Successfully saved {} paying records", list.size()));
	}

	/**
	 * Memvalidasi apakah satu data tagihan layak masuk daftar koleksi.
	 * Tagihan dianggap tidak valid (dan akan dibuang) jika:
	 * <ul>
	 *   <li>Objeknya null</li>
	 *   <li>Field {@code dayLate} bukan angka yang valid</li>
	 *   <li>Hari keterlambatan lebih dari 125 hari</li>
	 * </ul>
	 *
	 * @param bills objek tagihan yang ingin divalidasi
	 * @return {@code true} kalau tagihan TIDAK valid (harus dibuang),
	 *         {@code false} kalau tagihan valid dan boleh masuk daftar
	 */
	private boolean isNotValidBills(Bills bills) {
		if (bills == null) {
			return true;
		}
		try {
			int dayLate = Integer.parseInt(bills.getDayLate());
			if (dayLate > 125) {
				log.info("Removed {} because above 120 days", bills.getName());
				return true;
			}
		} catch (NumberFormatException e) {
			log.info("Removed {} number exception ", bills.getName());
			return true;
		}
		log.info("Selecting {}", bills.getName());
		return false;
	}
}
