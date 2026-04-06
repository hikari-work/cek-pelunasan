package org.cekpelunasan.core.repository;

import org.cekpelunasan.core.entity.CreditHistory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repository untuk mengakses riwayat pengecekan kredit (SLIK) dari koleksi MongoDB {@code credit_history}.
 * <p>
 * Menyediakan operasi dasar CRUD (lewat {@link ReactiveMongoRepository}) dan satu query
 * tambahan untuk memfilter riwayat berdasarkan status hasil pengecekan. Query yang
 * lebih kompleks (misalnya filter berdasarkan rentang tanggal atau kombinasi field)
 * dikerjakan di layer service menggunakan {@code ReactiveMongoTemplate}.
 * </p>
 *
 * <p>Catatan: JpaSpecificationExecutor dihapus karena ini repository MongoDB reaktif,
 * bukan JPA. Query kompleks diimplementasikan di {@code CreditHistoryService}.</p>
 */
// NOTE: Specification/JpaSpecificationExecutor removed — query implemented in CreditHistoryService via ReactiveMongoTemplate
@Repository
public interface CreditHistoryRepository extends ReactiveMongoRepository<CreditHistory, String> {

    /**
     * Mencari semua riwayat pengecekan kredit berdasarkan status hasilnya,
     * tanpa mempedulikan huruf besar atau kecil.
     * <p>
     * Misalnya, memanggil dengan {@code "clear"} akan mengembalikan semua record
     * yang statusnya "CLEAR", "clear", atau "Clear".
     * </p>
     *
     * @param status status pengecekan yang dicari (misalnya "CLEAR", "BLACKLIST")
     * @return stream riwayat pengecekan yang statusnya cocok
     */
    Flux<CreditHistory> findByStatusIgnoreCase(String status);
}
