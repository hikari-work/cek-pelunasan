package org.cekpelunasan.core.service.log;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.DataUpdateLog;
import org.cekpelunasan.core.repository.DataUpdateLogRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mengelola waktu terakhir pembaruan data untuk setiap jenis koleksi.
 * Menyediakan metode untuk menyimpan timestamp setelah import CSV,
 * dan untuk menghasilkan warning jika data yang ditampilkan bukan dari hari ini (UTC+7).
 */
@Service
@RequiredArgsConstructor
public class DataUpdateLogService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	private static final ZoneOffset WIB = ZoneOffset.ofHours(7);

	private final DataUpdateLogRepository repository;
	private final Map<String, LocalDate> cache = new ConcurrentHashMap<>();

	/**
	 * Menyimpan timestamp pembaruan data untuk jenis koleksi tertentu.
	 * Cache di-update sekaligus agar pembacaan berikutnya tidak perlu ke database.
	 *
	 * @param dataType nama jenis data, misalnya "TAGIHAN" atau "SAVING"
	 * @return {@link Mono} yang selesai setelah timestamp berhasil disimpan
	 */
	public Mono<Void> saveUpdateTimestamp(String dataType) {
		LocalDateTime now = LocalDateTime.now(WIB);
		cache.put(dataType, now.toLocalDate());
		return repository.save(new DataUpdateLog(dataType, now)).then();
	}

	/**
	 * Mengambil tanggal terakhir pembaruan data untuk jenis koleksi tertentu.
	 * Membaca dari cache terlebih dahulu; jika tidak ada, baru ke database.
	 *
	 * @param dataType nama jenis data
	 * @return {@link Optional} berisi tanggal terakhir update, atau kosong jika belum pernah diperbarui
	 */
	public Optional<LocalDate> getLastUpdateDate(String dataType) {
		LocalDate cached = cache.get(dataType);
		if (cached != null) {
			return Optional.of(cached);
		}
		return repository.findById(dataType)
			.subscribeOn(Schedulers.boundedElastic())
			.map(log -> log.getUpdatedAt().toLocalDate())
			.blockOptional();
	}

	/**
	 * Menghasilkan warning Telegram (format italic markdown) jika data bukan dari hari ini.
	 * Mengembalikan string kosong jika data sudah up-to-date.
	 *
	 * @param dataType nama jenis data
	 * @return string warning berformat Telegram, atau string kosong
	 */
	public String telegramWarning(String dataType) {
		return getLastUpdateDate(dataType)
			.filter(date -> !date.isEqual(LocalDate.now(WIB)))
			.map(date -> "\n\n⚠️ _Data terakhir diupdate tanggal " + date.format(DATE_FORMAT) + "_")
			.orElse("");
	}

	/**
	 * Menghasilkan warning WhatsApp (plain text) jika data bukan dari hari ini.
	 * Mengembalikan string kosong jika data sudah up-to-date.
	 *
	 * @param dataType nama jenis data
	 * @return string warning plain text, atau string kosong
	 */
	public String whatsAppWarning(String dataType) {
		return getLastUpdateDate(dataType)
			.filter(date -> !date.isEqual(LocalDate.now(WIB)))
			.map(date -> "\n\n⚠️ Data terakhir diupdate tanggal " + date.format(DATE_FORMAT))
			.orElse("");
	}
}
