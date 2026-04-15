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

/**
 * Mengelola data koleksi tas — yaitu data tagihan kelompok nasabah yang
 * dikelola secara bersama dalam satu kelompok (mirip arisan atau KUR kelompok).
 * Class ini menyediakan fitur pencarian data per kelompok dan impor data CSV.
 *
 * <p>Nominal tagihan disimpan dalam format rupiah yang sudah diformat
 * (contoh: "Rp1.500.000") agar langsung bisa ditampilkan ke pengguna
 * tanpa perlu konversi tambahan.</p>
 */
@Service
@RequiredArgsConstructor
public class KolekTasService {

	private static final Logger log = LoggerFactory.getLogger(KolekTasService.class);
	private final KolekTasRepository kolekTasRepository;

	/**
	 * Mencari data koleksi berdasarkan nama kelompok (tidak case-sensitive),
	 * dengan hasil dibagi per halaman. Nomor halaman dimulai dari 1, bukan 0,
	 * sesuai konvensi tampilan di bot.
	 *
	 * @param kelompok nama kelompok yang ingin dicari
	 * @param page     nomor halaman tampilan (dimulai dari 1)
	 * @param size     jumlah data per halaman
	 * @return {@link Mono} berisi halaman data koleksi yang sesuai
	 */
	public Mono<Page<KolekTas>> findKolekByKelompok(String kelompok, int page, int size) {
		int zeroBasedPage = page > 0 ? page - 1 : 0;
		PageRequest pageRequest = PageRequest.of(zeroBasedPage, size);
		Mono<List<KolekTas>> content = kolekTasRepository.findByKelompokIgnoreCase(kelompok, pageRequest).collectList();
		Mono<Long> total = kolekTasRepository.countByKelompokIgnoreCase(kelompok).defaultIfEmpty(0L);
		return Mono.zip(content, total)
			.map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
	}

	/**
	 * Menyimpan daftar data koleksi ke database sekaligus (batch save).
	 *
	 * @param kolekTas daftar data koleksi yang ingin disimpan
	 * @return {@link Mono} yang selesai ketika semua data berhasil disimpan
	 */
	public Mono<Void> saveAll(@NonNull List<KolekTas> kolekTas) {
		return kolekTasRepository.saveAll(kolekTas).then();
	}

	/**
	 * Menghapus seluruh data koleksi dari database. Dipanggil sebelum impor
	 * data CSV baru agar tidak ada duplikasi.
	 *
	 * @return {@link Mono} yang selesai ketika penghapusan berhasil
	 */
	public Mono<Void> deleteAll() {
		return kolekTasRepository.deleteAll();
	}

	/**
	 * Membaca file CSV koleksi, menghapus data lama, lalu menyimpan data baru
	 * secara batch (500 baris sekaligus). Baris pertama (header) di CSV
	 * dilewati secara otomatis. Proses berjalan di thread terpisah dan akan
	 * mencoba ulang sampai 3 kali jika terjadi kegagalan saat menyimpan.
	 *
	 * @param path lokasi file CSV yang berisi data koleksi tas
	 * @return {@link Mono} yang selesai ketika seluruh data berhasil diimpor
	 */
	public Mono<Void> parseCsvAndSave(Path path) {
		return parseCsvAndSave(path, 0L, null);
	}

	/**
	 * Versi dengan progress callback. Callback dipanggil setiap ~5% progres
	 * (maks 20 kali) dengan argumen = jumlah baris yang sudah diproses.
	 *
	 * @param path       lokasi file CSV yang berisi data koleksi tas
	 * @param total      total baris data (tanpa header)
	 * @param onProgress callback jumlah baris terproses; boleh {@code null}
	 * @return {@link Mono} yang selesai ketika seluruh data berhasil diimpor
	 */
	public Mono<Void> parseCsvAndSave(Path path, long total, java.util.function.LongConsumer onProgress) {
		long updateInterval = Math.max(500L, total / 20);
		java.util.concurrent.atomic.AtomicLong processed = new java.util.concurrent.atomic.AtomicLong(0);
		java.util.concurrent.atomic.AtomicLong lastStep = new java.util.concurrent.atomic.AtomicLong(-1);

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
				.flatMap(batch -> {
					int batchSize = batch.size();
					return saveAll(batch)
						.retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
						.doOnSuccess(v -> {
							if (onProgress == null) return;
							long done = processed.addAndGet(batchSize);
							long step = done / updateInterval;
							if (step > lastStep.getAndSet(step) || done >= total) {
								onProgress.accept(done);
							}
						});
				}, Runtime.getRuntime().availableProcessors())
				.then())
			.doFinally(signal -> System.gc());
	}

	/**
	 * Mengonversi satu baris CSV menjadi objek {@link KolekTas}. Nominal
	 * otomatis diformat ke format rupiah menggunakan {@link #formatRupiah}.
	 * Urutan kolom: kelompok, kantor, rekening, nama, alamat, noHp, kolek,
	 * nominal, accountOfficer, cif.
	 *
	 * @param line satu baris CSV dalam bentuk array string
	 * @return objek {@link KolekTas} yang sudah terisi
	 */
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

	/**
	 * Memformat angka menjadi string rupiah dengan pemisah ribuan menggunakan
	 * titik dan pemisah desimal menggunakan koma (standar Indonesia).
	 * Contoh: 1500000 → "Rp1.500.000".
	 *
	 * @param amount nilai nominal dalam satuan rupiah
	 * @return string rupiah yang sudah diformat, atau "Rp0" jika null
	 */
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
