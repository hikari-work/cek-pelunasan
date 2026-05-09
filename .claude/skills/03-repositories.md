---
name: repositories
description: Semua ReactiveMongoRepository di project cek-pelunasan — method signatures dan query yang tersedia untuk setiap collection MongoDB
---

# Repositories Layer

Package: `org.cekpelunasan.core.repository`  
Semua extend `ReactiveMongoRepository<T, ID>` — mengembalikan `Mono<T>` atau `Flux<T>`.

---

## BillsRepository
**File**: `core/repository/BillsRepository.java`  
**Entity**: `Bills` | **Collection**: `tagihan`

```java
// Pagination per AO dengan filter status bayar
Flux<Bills> findByAccountOfficerAndPayDown(String ao, Boolean payDown, Pageable pageable);

// Per cabang + status, sorted by AO
Flux<Bills> findByBranchAndPayDownOrderByAccountOfficer(String branch, Boolean payDown);

// Fuzzy name search per cabang
Flux<Bills> findByNameContainingIgnoreCaseAndBranch(String name, String branch);

// Lookup by SPK number
Mono<Bills> findByNoSpk(String noSpk);

// Count by branch + payDown status
Mono<Long> countByBranchAndPayDown(String branch, Boolean payDown);

// Count by AO + payDown status
Mono<Long> countByAccountOfficerAndPayDown(String ao, Boolean payDown);

// $expr query: minimum interest per AO
@Query("{'accountOfficer': ?0, '$expr': {'$lte': ['$interest', ?1]}}")
Flux<Bills> findByAccountOfficerWithMinInterest(String ao, Double maxInterest);

// Semua tagihan per cabang
Flux<Bills> findByBranch(String branch);

// Tagihan per AO
Flux<Bills> findByAccountOfficer(String ao);
```

---

## UserRepository
**File**: `core/repository/UserRepository.java`  
**Entity**: `User` | **Collection**: `users`

```java
// Lookup by chatId (primary key)
Mono<User> findById(Long chatId);

// Lookup by kode AO
Mono<User> findByUserCode(String userCode);

// Semua user aktif
Flux<User> findByIsActive(Boolean isActive);

// Semua user per role
Flux<User> findByRole(AccountOfficerRoles role);
```

---

## CreditHistoryRepository
**File**: `core/repository/CreditHistoryRepository.java`  
**Entity**: `CreditHistory` | **Collection**: `credit_history`

```java
// Riwayat pengecekan per chatId
Flux<CreditHistory> findByChatId(Long chatId);

// Riwayat per KTP number
Flux<CreditHistory> findByKtpNumber(String ktpNumber);

// Riwayat terbaru per chatId
Flux<CreditHistory> findByChatIdOrderByCheckedAtDesc(Long chatId);
```

---

## KolekTasRepository
**File**: `core/repository/KolekTasRepository.java`  
**Entity**: `KolekTas` | **Collection**: `kolek_tas`

```java
// Daftar kolek per AO
Flux<KolekTas> findByAccountOfficer(String accountOfficer);

// Daftar kolek per cabang
Flux<KolekTas> findByBranch(String branch);

// Filter by status kunjungan
Flux<KolekTas> findByAccountOfficerAndIsVisited(String ao, Boolean visited);

// Lookup by SPK
Mono<KolekTas> findByNoSpk(String noSpk);
```

---

## PayingRepository
**File**: `core/repository/PayingRepository.java`  
**Entity**: `Paying` | **Collection**: `paying`

```java
// Status bayar per SPK
Mono<Paying> findByNoSpk(String noSpk);

// Semua yang sudah dibayar
Flux<Paying> findByIsPaid(Boolean isPaid);

// Pembayaran oleh chatId tertentu
Flux<Paying> findByPaidBy(Long chatId);
```

---

## PaymentDetailsRepository
**File**: `core/repository/PaymentDetailsRepository.java`  
**Entity**: `PaymentDetails` | **Collection**: `payment_details`

```java
// Detail per paymentId
Flux<PaymentDetails> findByPaymentId(String paymentId);

// Detail per type (PRINCIPAL/INTEREST/PENALTY)
Flux<PaymentDetails> findByType(String type);
```

---

## SavingsRepository
**File**: `core/repository/SavingsRepository.java`  
**Entity**: `Savings` | **Collection**: `savings`

```java
// Lookup by nomor rekening
Mono<Savings> findByTabId(String tabId);

// Fuzzy name search (case insensitive)
Flux<Savings> findByNameContainingIgnoreCase(String name);

// Per cabang
Flux<Savings> findByBranch(String branch);
```

---

## SimulasiRepository
**File**: `core/repository/SimulasiRepository.java`  
**Entity**: `Simulasi` | **Collection**: `simulasi`

```java
// Semua baris simulasi per SPK
Flux<Simulasi> findByNoSpk(String noSpk);

// Simulasi per SPK sorted by bulan
Flux<Simulasi> findByNoSpkOrderByMonthNumber(String noSpk);
```

---

## SlikNotifiedFileRepository
**File**: `core/repository/SlikNotifiedFileRepository.java`  
**Entity**: `SlikNotifiedFile` | **Collection**: `slik_notified_file`

```java
// Cek apakah file sudah pernah dinotifikasi
Mono<SlikNotifiedFile> findByFileName(String fileName);

// Semua yang sudah dinotifikasi ke chatId tertentu
Flux<SlikNotifiedFile> findByNotifiedTo(Long chatId);
```

---

## DataUpdateLogRepository
**File**: `core/repository/DataUpdateLogRepository.java`  
**Entity**: `DataUpdateLog` | **Collection**: `data_update_log`

```java
// Log update per tipe data (tagihan/savings/dll)
Mono<DataUpdateLog> findByDataType(String dataType);
```

---

## Cara Menggunakan Repository

### Pattern Dasar (Reactive)
```java
@Autowired
BillsRepository billsRepository;

// Fetch single
billsRepository.findByNoSpk("SPK-001")
    .subscribe(bills -> log.info("Found: {}", bills));

// Fetch multiple dengan transformasi
billsRepository.findByAccountOfficer("AO-01")
    .map(Bills::getTotalBill)
    .reduce(0.0, Double::sum)
    .subscribe(total -> log.info("Total: {}", total));
```

### Inject di Service
```java
@Service
@RequiredArgsConstructor
public class BillService {
    private final BillsRepository billsRepository;
    // gunakan billsRepository.method() yang return Mono/Flux
}
```

> **PENTING**: Jangan gunakan `.block()` di production code — selalu chain dengan operator Reactor.
