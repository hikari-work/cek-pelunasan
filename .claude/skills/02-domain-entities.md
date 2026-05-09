---
name: domain-entities
description: Semua domain entity MongoDB di project cek-pelunasan — field, anotasi, dan relasi antar entity
---

# Domain Entities

Package: `org.cekpelunasan.core.entity`  
Semua entity menggunakan Lombok (`@Data`, `@Builder`, `@Document`) dan `@Document` Spring Data MongoDB.

---

## User
**File**: `core/entity/User.java`  
**Collection**: `users`  
**Primary Key**: `chatId` (Long)

```java
@Document("users")
class User {
    Long chatId;           // Telegram chat ID (primary key)
    String userCode;       // Kode AO/user
    String branchCode;     // Kode cabang preferensi
    AccountOfficerRoles role; // AO | PIMP | ADMIN
    Boolean isActive;      // Status aktif/nonaktif
}
```

---

## Bills
**File**: `core/entity/Bills.java`  
**Collection**: `tagihan`  
**Primary Key**: `noSpk` (String)

```java
@Document("tagihan")
class Bills {
    String noSpk;          // Nomor SPK kredit (PK)
    String name;           // Nama nasabah
    String branch;         // Kode cabang
    String accountOfficer; // Kode AO yang bertanggung jawab
    Double principal;      // Pokok tagihan
    Double interest;       // Bunga
    Double penalty;        // Denda
    Double totalBill;      // Total (pokok + bunga + denda)
    Boolean payDown;       // Flag sudah dibayar/lunas
    LocalDate dueDate;     // Tanggal jatuh tempo
    String nasabahId;      // ID nasabah (no KTP/CIF)
}
```

---

## Savings
**File**: `core/entity/Savings.java`  
**Collection**: `savings`

```java
@Document("savings")
class Savings {
    String id;
    String tabId;          // Nomor rekening tabungan
    String name;           // Nama pemilik rekening
    String branch;         // Kode cabang
    Double balance;        // Saldo rekening
    Double minBalance;     // Saldo minimum/blokir
    Double availableBalance; // Saldo yang bisa diambil
}
```

---

## Paying
**File**: `core/entity/Paying.java`  
**Collection**: `paying`

```java
@Document("paying")
class Paying {
    String id;
    String noSpk;          // Referensi ke Bills.noSpk
    Boolean isPaid;        // Flag status pembayaran
    LocalDateTime paidAt;  // Waktu pembayaran
    Long paidBy;           // chatId yang menandai bayar
}
```

---

## CreditHistory
**File**: `core/entity/CreditHistory.java`  
**Collection**: `credit_history`

```java
@Document("credit_history")
class CreditHistory {
    String id;
    Long chatId;           // chatId yang melakukan pengecekan
    String ktpNumber;      // Nomor KTP yang dicek
    String name;           // Nama yang dicek
    LocalDateTime checkedAt; // Waktu pengecekan
    String result;         // Hasil/ringkasan SLIK
}
```

---

## KolekTas
**File**: `core/entity/KolekTas.java`  
**Collection**: `kolek_tas`

```java
@Document("kolek_tas")
class KolekTas {
    String id;
    String name;           // Nama nasabah
    String noSpk;          // Nomor SPK
    String accountOfficer; // Kode AO
    String branch;         // Kode cabang
    String notes;          // Catatan kunjungan
    LocalDate visitDate;   // Tanggal kunjungan
    Boolean isVisited;     // Sudah dikunjungi?
}
```

---

## Simulasi
**File**: `core/entity/Simulasi.java`  
**Collection**: `simulasi`

```java
@Document("simulasi")
class Simulasi {
    String id;
    String noSpk;          // Referensi ke Bills.noSpk
    Integer monthNumber;   // Nomor bulan simulasi
    LocalDate dueDate;     // Tanggal jatuh tempo bulan ini
    Double principalPayment; // Cicilan pokok
    Double interestPayment;  // Cicilan bunga
    Double remainingPrincipal; // Sisa pokok
}
```

---

## SimulasiResult
**File**: `core/entity/SimulasiResult.java`  
**Collection**: `simulasi_result`

```java
@Document("simulasi_result")
class SimulasiResult {
    String id;
    String noSpk;
    Double totalPayment;     // Total pembayaran simulasi
    Double earlyPayOff;      // Diskon pelunasan awal
    Integer remainingMonths; // Sisa bulan
    LocalDateTime createdAt;
}
```

---

## Payment
**File**: `core/entity/Payment.java`  
**Collection**: `payment`

```java
@Document("payment")
class Payment {
    String id;
    String noSpk;
    Double amount;           // Jumlah pembayaran
    Long paidBy;             // chatId
    String status;           // PENDING | SUCCESS | FAILED
    LocalDateTime createdAt;
}
```

---

## PaymentDetails
**File**: `core/entity/PaymentDetails.java`  
**Collection**: `payment_details`

```java
@Document("payment_details")
class PaymentDetails {
    String id;
    String paymentId;        // Referensi ke Payment.id
    String description;      // Keterangan komponen bayar
    Double amount;           // Jumlah komponen
    String type;             // PRINCIPAL | INTEREST | PENALTY
}
```

---

## DataUpdateLog
**File**: `core/entity/DataUpdateLog.java`  
**Collection**: `data_update_log`

```java
@Document("data_update_log")
class DataUpdateLog {
    String dataType;         // "tagihan" | "savings" | dll (PK)
    LocalDateTime updatedAt; // Waktu update terakhir
    String updatedBy;        // Identifier updater
}
```

---

## SlikNotifiedFile
**File**: `core/entity/SlikNotifiedFile.java`  
**Collection**: `slik_notified_file`

```java
@Document("slik_notified_file")
class SlikNotifiedFile {
    String id;
    String fileName;         // Nama file SLIK
    LocalDateTime notifiedAt;// Waktu notifikasi dikirim
    Long notifiedTo;         // chatId penerima
}
```

---

## AccountOfficerRoles (Enum)
**File**: `core/entity/AccountOfficerRoles.java`

```java
enum AccountOfficerRoles {
    AO,    // Account Officer — basic access
    PIMP,  // Pimpinan — read-only leadership
    ADMIN  // Administrator — full access
}
```

---

## Relasi Antar Entity
```
Bills (noSpk) ←── Paying.noSpk
Bills (noSpk) ←── Simulasi.noSpk
Bills (noSpk) ←── SimulasiResult.noSpk
Bills (noSpk) ←── Payment.noSpk
Payment (id) ←── PaymentDetails.paymentId
User (chatId) ←── CreditHistory.chatId
User (chatId) ←── Paying.paidBy
User (chatId) ←── Payment.paidBy
```

> Semua relasi bersifat soft reference (tidak ada foreign key constraint — ini MongoDB).
