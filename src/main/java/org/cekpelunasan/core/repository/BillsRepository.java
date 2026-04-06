package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.Bills;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository untuk mengakses data tagihan kredit nasabah dari koleksi MongoDB {@code tagihan}.
 * <p>
 * Menyediakan berbagai query yang dibutuhkan oleh fitur-fitur bot, mulai dari
 * menampilkan daftar tagihan per AO, per cabang, per kios, sampai mencari tagihan
 * berdasarkan nama nasabah. Semua operasi berjalan secara reaktif (non-blocking)
 * menggunakan Project Reactor.
 * </p>
 */
@Repository
public interface BillsRepository extends ReactiveMongoRepository<Bills, String> {

    /**
     * Mengambil daftar tagihan milik AO tertentu dengan status {@code payDown} yang ditentukan,
     * digunakan untuk menampilkan tagihan per halaman (paginasi).
     *
     * @param accountOfficer kode AO yang tagihannya ingin ditampilkan
     * @param payDown        status pay down yang difilter
     * @param pageable       konfigurasi halaman dan ukuran data
     * @return stream tagihan yang cocok
     */
    Flux<Bills> findByAccountOfficerAndPayDown(String accountOfficer, String payDown, Pageable pageable);

    /**
     * Menghitung jumlah tagihan milik AO tertentu dengan status {@code payDown} tertentu.
     * Dipakai untuk menghitung total halaman dalam paginasi.
     *
     * @param accountOfficer kode AO yang dihitung tagihannya
     * @param payDown        status pay down yang difilter
     * @return jumlah dokumen yang cocok
     */
    Mono<Long> countByAccountOfficerAndPayDown(String accountOfficer, String payDown);

    /**
     * Mengambil daftar tagihan se-cabang dengan status {@code payDown} tertentu,
     * diurutkan berdasarkan nama AO untuk memudahkan pimpinan melihat rekapan per AO.
     *
     * @param branch   kode cabang
     * @param payDown  status pay down yang difilter
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream tagihan yang cocok, terurut per AO
     */
    Flux<Bills> findByBranchAndPayDownOrderByAccountOfficer(String branch, String payDown, Pageable pageable);

    /**
     * Menghitung jumlah tagihan se-cabang dengan status {@code payDown} tertentu.
     * Dipakai untuk menghitung total halaman dalam paginasi tampilan pimpinan.
     *
     * @param branch  kode cabang
     * @param payDown status pay down yang difilter
     * @return jumlah dokumen yang cocok
     */
    Mono<Long> countByBranchAndPayDown(String branch, String payDown);

    /**
     * Mencari tagihan berdasarkan nama nasabah (pencarian parsial, tidak peka huruf besar/kecil)
     * dalam satu cabang tertentu, dengan paginasi.
     *
     * @param name     sebagian atau seluruh nama nasabah yang dicari
     * @param branch   kode cabang tempat pencarian dilakukan
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream tagihan yang nama nasabahnya mengandung kata kunci yang diberikan
     */
    Flux<Bills> findByNameContainingIgnoreCaseAndBranch(String name, String branch, Pageable pageable);

    /**
     * Menghitung jumlah tagihan yang nama nasabahnya cocok dengan kata kunci dalam satu cabang.
     * Dipakai untuk menghitung total halaman pada fitur pencarian nama.
     *
     * @param name   kata kunci pencarian nama nasabah
     * @param branch kode cabang
     * @return jumlah dokumen yang cocok
     */
    Mono<Long> countByNameContainingIgnoreCaseAndBranch(String name, String branch);

    /**
     * Mengambil tagihan di suatu lokasi kantor yang punya tunggakan minimal bunga atau pokok
     * (jumlah {@code minInterest + minPrincipal} lebih dari 0). Dipakai untuk laporan
     * nasabah yang punya kewajiban minimum belum terpenuhi, dengan paginasi.
     * <p>
     * Parameter {@code minInterest} dan {@code minPrincipal} tidak dipakai sebagai nilai filter
     * langsung — query MongoDB yang menentukan logika filter sebenarnya.
     * </p>
     *
     * @param minInterest  tidak dipakai sebagai filter (placeholder query)
     * @param minPrincipal tidak dipakai sebagai filter (placeholder query)
     * @param branch       kode lokasi kantor (dipakai sebagai {@code officeLocation})
     * @param pageable     konfigurasi halaman dan ukuran data
     * @return stream tagihan yang punya tunggakan minimum
     */
    @Query("{ 'officeLocation': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Flux<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndBranch(Long minInterest, Long minPrincipal, String branch, Pageable pageable);

    /**
     * Menghitung tagihan di suatu lokasi kantor yang punya tunggakan minimal bunga atau pokok.
     *
     * @param minInterest  tidak dipakai sebagai filter (placeholder query)
     * @param minPrincipal tidak dipakai sebagai filter (placeholder query)
     * @param branch       kode lokasi kantor (dipakai sebagai {@code officeLocation})
     * @return jumlah tagihan yang punya tunggakan minimum
     */
    @Query("{ 'officeLocation': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Mono<Long> countByMinInterestOrMinPrincipalIsGreaterThanAndBranch(Long minInterest, Long minPrincipal, String branch);

    /**
     * Mengambil tagihan di kios tertentu (khusus cabang 1075) yang punya tunggakan minimum
     * bunga atau pokok lebih dari 0, dengan paginasi.
     *
     * @param minInterest  tidak dipakai sebagai filter (placeholder query)
     * @param minPrincipal tidak dipakai sebagai filter (placeholder query)
     * @param kios         nama atau kode kios yang dituju
     * @param pageable     konfigurasi halaman dan ukuran data
     * @return stream tagihan di kios tersebut yang punya tunggakan minimum
     */
    @Query("{ 'branch': '1075', 'kios': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Flux<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndKios(Long minInterest, Long minPrincipal, String kios, Pageable pageable);

    /**
     * Mengambil tagihan berdasarkan kombinasi cabang dan kios, difilter hanya yang punya
     * total minimum (minInterest + minPrincipal) melebihi {@code minTotal}, dengan paginasi.
     * Versi lebih fleksibel karena parameter cabang dan nilai minimum bisa dikustomisasi.
     *
     * @param branch   kode cabang
     * @param kios     nama atau kode kios
     * @param minTotal nilai minimum total (minInterest + minPrincipal) yang harus dilampaui
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream tagihan yang memenuhi semua kriteria
     */
    @Query("{ 'branch': ?0, 'kios': ?1, '$expr': { '$gt': [{ '$add': [{ '$ifNull': ['$minInterest', 0] }, { '$ifNull': ['$minPrincipal', 0] }] }, ?2] } }")
    Flux<Bills> findByBranchAndKiosAndTotalMin(String branch, String kios, Long minTotal, Pageable pageable);

    /**
     * Mengambil tagihan di suatu cabang yang total minimumnya melebihi {@code minTotal},
     * tanpa filter kios, dengan paginasi.
     *
     * @param branch   kode cabang
     * @param minTotal nilai minimum total (minInterest + minPrincipal) yang harus dilampaui
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream tagihan yang memenuhi kriteria
     */
    @Query("{ 'branch': ?0, '$expr': { '$gt': [{ '$add': [{ '$ifNull': ['$minInterest', 0] }, { '$ifNull': ['$minPrincipal', 0] }] }, ?1] } }")
    Flux<Bills> findByBranchAndTotalMin(String branch, Long minTotal, Pageable pageable);

    /**
     * Mengambil tagihan di kios tertentu (khusus cabang 1075) yang total minimumnya
     * melebihi {@code minTotal}, dengan paginasi.
     *
     * @param kios     nama atau kode kios
     * @param minTotal nilai minimum total (minInterest + minPrincipal) yang harus dilampaui
     * @param pageable konfigurasi halaman dan ukuran data
     * @return stream tagihan di kios tersebut yang memenuhi kriteria
     */
    @Query("{ 'branch': '1075', 'kios': ?0, '$expr': { '$gt': [{ '$add': [{ '$ifNull': ['$minInterest', 0] }, { '$ifNull': ['$minPrincipal', 0] }] }, ?1] } }")
    Flux<Bills> findByKiosAndTotalMin(String kios, Long minTotal, Pageable pageable);

    /**
     * Mengambil tagihan milik AO tertentu yang punya tunggakan minimum bunga atau pokok,
     * dengan paginasi.
     *
     * @param minInterest  tidak dipakai sebagai filter (placeholder query)
     * @param minPrincipal tidak dipakai sebagai filter (placeholder query)
     * @param accountOfficer kode AO yang dicari tagihannya
     * @param pageable     konfigurasi halaman dan ukuran data
     * @return stream tagihan AO tersebut yang punya tunggakan minimum
     */
    @Query("{ 'accountOfficer': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Flux<Bills> findByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(Long minInterest, Long minPrincipal, String accountOfficer, Pageable pageable);

    /**
     * Menghitung tagihan milik AO tertentu yang punya tunggakan minimum bunga atau pokok.
     *
     * @param minInterest  tidak dipakai sebagai filter (placeholder query)
     * @param minPrincipal tidak dipakai sebagai filter (placeholder query)
     * @param accountOfficer kode AO yang dihitung tagihannya
     * @return jumlah tagihan yang punya tunggakan minimum
     */
    @Query("{ 'accountOfficer': ?2, '$expr': { '$gt': [{ '$add': ['$minInterest', '$minPrincipal'] }, 0] } }")
    Mono<Long> countByMinInterestOrMinPrincipalIsGreaterThanAndAccountOfficer(Long minInterest, Long minPrincipal, String accountOfficer);

    /**
     * Mengambil semua tagihan di suatu cabang yang tanggal jatuh temponya diawali
     * dengan string {@code dueDate} yang diberikan. Berguna untuk mencari tagihan
     * yang jatuh tempo pada bulan atau tahun tertentu.
     *
     * @param branch  kode cabang
     * @param dueDate awalan tanggal jatuh tempo yang dicari (misalnya "2024-01")
     * @return stream tagihan yang cocok
     */
    @Query("{ 'branch': ?0, 'dueDate': { '$regex': '^?1' } }")
    Flux<Bills> findByBranchAndDueDateContaining(String branch, String dueDate);

    /**
     * Mengambil semua tagihan di suatu cabang berdasarkan status realisasi,
     * dengan pencocokan awalan yang tidak peka huruf besar/kecil.
     *
     * @param branch      kode cabang
     * @param realization teks status realisasi yang dicari
     * @return stream tagihan yang cocok
     */
    @Query("{ 'branch': ?0, 'realization': { '$regex': '^?1', '$options': 'i' } }")
    Flux<Bills> findByBranchAndRealizationContaining(String branch, String realization);

    /**
     * Mencari tagihan dari semua cabang yang tanggal jatuh temponya mengandung string tertentu.
     *
     * @param dueDate bagian tanggal jatuh tempo yang dicari
     * @return stream semua tagihan yang cocok lintas cabang
     */
    Flux<Bills> findByDueDateContaining(String dueDate);

    /**
     * Mencari tagihan dari semua cabang berdasarkan status realisasi (pencarian parsial,
     * tidak peka huruf besar/kecil).
     *
     * @param realization bagian teks status realisasi yang dicari
     * @return stream semua tagihan yang cocok lintas cabang
     */
    Flux<Bills> findByRealizationIsContainingIgnoreCase(String realization);

    /**
     * Mengambil seluruh tagihan yang dimiliki oleh suatu cabang tanpa filter tambahan.
     * Dipakai untuk keperluan ekspor data atau rekap keseluruhan.
     *
     * @param branch kode cabang
     * @return stream semua tagihan di cabang tersebut
     */
    Flux<Bills> findAllByBranch(String branch);

    /**
     * Mengambil tagihan di suatu cabang yang nomor SPK-nya tidak ada dalam daftar yang diberikan.
     * Dipakai untuk menemukan tagihan yang belum lunas — dengan cara mengecualikan
     * SPK yang sudah ada di koleksi {@code paying}.
     *
     * @param branch    kode cabang
     * @param noSpkList daftar nomor SPK yang sudah lunas (akan dikecualikan dari hasil)
     * @return stream tagihan yang SPK-nya tidak ada dalam daftar tersebut
     */
    Flux<Bills> findByBranchAndNoSpkNotIn(String branch, Iterable<String> noSpkList);
}
