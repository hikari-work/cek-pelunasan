package org.cekpelunasan.core.service.paymentdetails;

import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.PaymentDetails;
import org.cekpelunasan.core.repository.PaymentDetailsRepository;
import org.cekpelunasan.core.service.log.DataUpdateLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.FileReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Mengelola data detail pembayaran angsuran AO yang bersumber dari export CSV DB2.
 * <p>
 * Setiap record diidentifikasi secara unik oleh kombinasi seluruh kolom data —
 * sehingga upload ulang baris yang identik dari DB2 tidak menghasilkan duplikasi.
 * Upload file dari tanggal berbeda akan menambah data baru secara kumulatif.
 * </p>
 * <p>
 * Proses import menggunakan strategi upsert (save dengan ID yang sudah diset)
 * dan retry otomatis sebanyak 3 kali dengan backoff 1 detik jika terjadi
 * kegagalan koneksi ke MongoDB.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PaymentDetailsService {

    private static final Logger log = LoggerFactory.getLogger(PaymentDetailsService.class);
    private static final String DATA_TYPE = "PAYMENT_DETAILS";

    private final PaymentDetailsRepository paymentDetailsRepository;
    private final DataUpdateLogService dataUpdateLogService;

    /**
     * Mengambil data angsuran untuk satu AO tertentu dengan paginasi.
     * Nomor halaman dimulai dari 1 sesuai konvensi tampilan di bot.
     *
     * @param kodeAo kode Account Officer
     * @param page   nomor halaman (dimulai dari 1)
     * @param size   jumlah data per halaman
     * @return {@link Mono} berisi halaman data angsuran AO tersebut
     */
    public Mono<Page<PaymentDetails>> findByKodeAo(String kodeAo, int page, int size) {
        int zeroBasedPage = page > 0 ? page - 1 : 0;
        PageRequest pageRequest = PageRequest.of(zeroBasedPage, size);
        Mono<List<PaymentDetails>> content = paymentDetailsRepository.findByKodeAo(kodeAo, pageRequest).collectList();
        Mono<Long> total = paymentDetailsRepository.countByKodeAo(kodeAo).defaultIfEmpty(0L);
        return Mono.zip(content, total)
            .map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
    }

    /**
     * Mengambil data angsuran per cabang pada tanggal tertentu dengan paginasi.
     *
     * @param kodeCabang kode cabang
     * @param tanggal    tanggal dalam format yyyyMMdd
     * @param page       nomor halaman (dimulai dari 1)
     * @param size       jumlah data per halaman
     * @return {@link Mono} berisi halaman data angsuran cabang tersebut
     */
    public Mono<Page<PaymentDetails>> findByCabangAndTanggal(String kodeCabang, String tanggal, int page, int size) {
        int zeroBasedPage = page > 0 ? page - 1 : 0;
        PageRequest pageRequest = PageRequest.of(zeroBasedPage, size);
        Mono<List<PaymentDetails>> content = paymentDetailsRepository.findByKodeCabangAndTanggal(kodeCabang, tanggal, pageRequest).collectList();
        Mono<Long> total = paymentDetailsRepository.countByKodeCabangAndTanggal(kodeCabang, tanggal).defaultIfEmpty(0L);
        return Mono.zip(content, total)
            .map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
    }

    /**
     * Mengambil data pelunasan (flag pelunasan = true) pada tanggal tertentu dengan paginasi.
     *
     * @param tanggal tanggal dalam format yyyyMMdd
     * @param page    nomor halaman (dimulai dari 1)
     * @param size    jumlah data per halaman
     * @return {@link Mono} berisi halaman data pelunasan
     */
    public Mono<Page<PaymentDetails>> findPelunasanByTanggal(String tanggal, int page, int size) {
        int zeroBasedPage = page > 0 ? page - 1 : 0;
        PageRequest pageRequest = PageRequest.of(zeroBasedPage, size);
        Mono<List<PaymentDetails>> content = paymentDetailsRepository.findByTanggalAndFlagPelunasan(tanggal, true, pageRequest).collectList();
        Mono<Long> total = paymentDetailsRepository.countByTanggalAndFlagPelunasan(tanggal, true).defaultIfEmpty(0L);
        return Mono.zip(content, total)
            .map(t -> new PageImpl<>(t.getT1(), pageRequest, t.getT2()));
    }

    /**
     * Mengambil seluruh data angsuran untuk satu AO pada tanggal tertentu.
     *
     * @param kodeAo  kode Account Officer
     * @param tanggal tanggal dalam format yyyyMMdd
     * @return {@link Flux} berisi semua record angsuran AO tersebut pada tanggal yang diminta
     */
    public Flux<PaymentDetails> findByKodeAoAndTanggal(String kodeAo, String tanggal) {
        return paymentDetailsRepository.findByKodeAoAndTanggal(kodeAo, tanggal);
    }

    /**
     * Membaca file CSV, lalu menyimpan data secara batch dengan strategi upsert.
     * <p>
     * Data lama <strong>tidak</strong> dihapus — data bersifat kumulatif per tanggal.
     * Upload file dari tanggal berbeda menambah data baru; upload ulang file yang sama
     * hanya menimpa record yang sudah ada (upsert via composite ID).
     * </p>
     * <p>
     * Baris pertama (header) di CSV dilewati otomatis. Proses berjalan di thread
     * terpisah dan akan mencoba ulang sampai 3 kali dengan backoff 1 detik jika
     * terjadi kegagalan saat menyimpan ke MongoDB.
     * </p>
     *
     * @param path lokasi file CSV yang berisi data pembayaran angsuran
     * @return {@link Mono} yang selesai ketika seluruh data berhasil diimpor
     */
    public Mono<Void> parseCsvAndSave(Path path) {
        return parseCsvAndSave(path, 0L, null);
    }

    /**
     * Versi dengan progress callback. Callback dipanggil setiap ~5% progres
     * (maks 20 kali) dengan argumen = jumlah baris yang sudah diproses.
     *
     * @param path       lokasi file CSV yang berisi data pembayaran angsuran
     * @param total      total baris data (tanpa header)
     * @param onProgress callback jumlah baris terproses; boleh {@code null}
     * @return {@link Mono} yang selesai ketika seluruh data berhasil diimpor
     */
    public Mono<Void> parseCsvAndSave(Path path, long total, java.util.function.LongConsumer onProgress) {
        long updateInterval = Math.max(500L, total / 20);
        java.util.concurrent.atomic.AtomicLong processed = new java.util.concurrent.atomic.AtomicLong(0);
        java.util.concurrent.atomic.AtomicLong lastStep = new java.util.concurrent.atomic.AtomicLong(-1);

        return Flux.<String[]>create(sink -> {
                try (CSVReader reader = new CSVReader(new FileReader(path.toFile()))) {
                    reader.readNext(); // skip header
                    String[] line;
                    while ((line = reader.readNext()) != null) {
                        sink.next(line);
                    }
                    sink.complete();
                } catch (Exception e) {
                    log.error("Gagal membaca file CSV Payment Details: {}", e.getMessage(), e);
                    sink.error(e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::mapToPaymentDetails)
            .buffer(500)
            .flatMap(batch -> {
                int batchSize = batch.size();
                return paymentDetailsRepository.saveAll(batch).then()
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
            .then()
            .doFinally(signal -> System.gc())
            .then(dataUpdateLogService.saveUpdateTimestamp(DATA_TYPE));
    }

    /**
     * Mengonversi satu baris CSV menjadi objek {@link PaymentDetails}.
     * <p>
     * ID komposit dibentuk dari seluruh kolom data yang digabung dengan pemisah
     * {@code |} untuk menjamin uniqueness. Flag pelunasan di-set {@code true}
     * jika denda atau penalti lebih dari nol.
     * </p>
     * <p>
     * Urutan kolom CSV: MLTPODT, MDLBRCO, MDLAOCO, MDLDLRF, MDLNAME, MLTSCTY,
     * MLTAMNT, MLTAMPE, MLTWAME.
     * </p>
     *
     * @param line satu baris CSV dalam bentuk array string
     * @return objek {@link PaymentDetails} yang sudah terisi
     */
    private PaymentDetails mapToPaymentDetails(String[] line) {
        String tanggal = line[0].trim();
        String kodeCabang = line[1].trim();
        String kodeAo = line[2].trim();
        String noSpk = line[3].trim();
        String nama = line[4].trim();
        String kodePosting = line[5].trim();
        long nominalAngsuran = parseLong(line[6]);
        long denda = parseLong(line[7]);
        long penalti = parseLong(line[8]);

        String id = tanggal + "|" + kodeCabang + "|" + kodeAo + "|" + noSpk + "|"
            + nama + "|" + kodePosting + "|" + nominalAngsuran + "|" + denda + "|" + penalti;

        return PaymentDetails.builder()
            .id(id)
            .tanggal(tanggal)
            .kodeCabang(kodeCabang)
            .kodeAo(kodeAo)
            .noSpk(noSpk)
            .nama(nama)
            .kodePosting(kodePosting)
            .nominalAngsuran(nominalAngsuran)
            .denda(denda)
            .penalti(penalti)
            .flagPelunasan(denda > 0 || penalti > 0)
            .build();
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
