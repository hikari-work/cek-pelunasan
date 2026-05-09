---
name: reactive-patterns
description: Pola Project Reactor (Mono/Flux) yang digunakan di project cek-pelunasan — cara kerja reactive pipeline, operator umum, anti-pattern, dan contoh nyata dari codebase
---

# Reactive Patterns (Project Reactor)

Project ini menggunakan **WebFlux + Project Reactor** secara penuh. Memahami Mono/Flux penting untuk menambah/debug fitur.

---

## Prinsip Utama

1. **Jangan `.block()`** di production code — mematikan keunggulan reactive
2. **Chain operator** bukan nested subscribe
3. **Error handling** via `.onErrorResume()` atau `.onErrorReturn()`
4. **Async I/O** via `.subscribeOn(Schedulers.boundedElastic())` untuk blocking ops

---

## Mono vs Flux

```java
// Mono<T> = 0 atau 1 item
Mono<Bills> bill = billsRepository.findByNoSpk("SPK-001");

// Flux<T> = 0 atau N items
Flux<Bills> bills = billsRepository.findByBranch("001");
```

---

## Operator yang Paling Sering Digunakan

### .map() — Transform satu item
```java
billsRepository.findByNoSpk(noSpk)
    .map(bills -> bills.getTotalBill())  // Bills → Double
    .subscribe(total -> sendMessage(chatId, "Total: " + total));
```

### .flatMap() — Transform ke Mono/Flux lain (async chain)
```java
userRepository.findById(chatId)
    .flatMap(user -> billsRepository.findByAccountOfficer(user.getUserCode()))
    // user → Flux<Bills>
    .subscribe(bill -> log.info("Bill: {}", bill.getName()));
```

### .filter() — Filter berdasarkan kondisi
```java
billsRepository.findByBranch("001")
    .filter(bill -> !bill.getPayDown())  // hanya belum bayar
    .subscribe(bill -> process(bill));
```

### .collectList() — Kumpulkan Flux ke List
```java
billsRepository.findByBranch("001")
    .collectList()
    .subscribe(list -> {
        String message = formatBillsList(list);
        sendMessage(chatId, message);
    });
```

### .take(n) — Ambil hanya N item pertama
```java
savingsRepository.findByNameContainingIgnoreCase(name)
    .take(5)  // max 5 hasil
    .collectList()
    .subscribe(list -> sendMessage(chatId, formatList(list)));
```

### .zipWith() — Gabungkan 2 Mono bersamaan
```java
billsRepository.findByNoSpk(noSpk)
    .zipWith(payingRepository.findByNoSpk(noSpk).defaultIfEmpty(new Paying()))
    .subscribe(tuple -> {
        Bills bills = tuple.getT1();
        Paying paying = tuple.getT2();
        sendMessage(chatId, formatDetail(bills, paying));
    });
```

### .switchIfEmpty() — Default jika kosong
```java
billsRepository.findByNoSpk(noSpk)
    .switchIfEmpty(Mono.error(new NotFoundException("SPK tidak ditemukan")))
    .subscribe(bills -> process(bills));
// ATAU
billsRepository.findByNoSpk(noSpk)
    .switchIfEmpty(Mono.just(new Bills()))  // default empty object
    .subscribe(bills -> process(bills));
```

### .defaultIfEmpty() — Default value jika kosong
```java
payingRepository.findByNoSpk(noSpk)
    .defaultIfEmpty(new Paying())  // paying = belum bayar
    .subscribe(paying -> checkStatus(paying));
```

### .onErrorResume() — Handle error dengan fallback
```java
slikService.checkByKtp(ktpNumber)
    .onErrorResume(e -> {
        log.error("SLIK error: {}", e.getMessage());
        return Mono.just(SlikResult.empty());
    })
    .subscribe(result -> sendPdf(chatId, result));
```

### .doOnNext() — Side effect tanpa mengubah stream
```java
billsRepository.findByBranch("001")
    .doOnNext(bill -> log.debug("Processing: {}", bill.getNoSpk()))
    .map(bill -> formatBill(bill))
    .subscribe(text -> sendMessage(chatId, text));
```

### .reduce() — Aggregate Flux ke single value
```java
billsRepository.findByAccountOfficer(ao)
    .map(Bills::getTotalBill)
    .reduce(0.0, Double::sum)
    .subscribe(total -> sendMessage(chatId, "Total: Rp " + total));
```

### Zip multiple Mono (lebih dari 2)
```java
Mono.zip(
    billsRepository.findByNoSpk(noSpk),
    payingRepository.findByNoSpk(noSpk).defaultIfEmpty(new Paying()),
    savingsRepository.findByTabId(tabId)
).subscribe(tuple -> {
    Bills bills = tuple.getT1();
    Paying paying = tuple.getT2();
    Savings savings = tuple.getT3();
    // proses semua sekaligus (parallel fetch)
});
```

---

## Blocking Operations (CPU-intensive / IO)

Playwright PDF generation, PDFBox parsing — ini **blocking** operations. Bungkus dengan:

```java
Mono.fromCallable(() -> {
    // blocking code di sini
    return pdfReader.extractText(pdfBytes);
})
.subscribeOn(Schedulers.boundedElastic())  // thread terpisah
.subscribe(text -> process(text));
```

---

## Subscribe Pattern

### Sederhana
```java
mono.subscribe(value -> handleValue(value));
```

### Dengan error handling
```java
mono.subscribe(
    value -> handleValue(value),
    error -> handleError(error)
);
```

### Dengan complete callback
```java
mono.subscribe(
    value -> handleValue(value),
    error -> handleError(error),
    () -> log.info("Stream selesai")
);
```

---

## Contoh Pattern Lengkap dari Codebase

### Pattern: Command handler → DB lookup → format → send
```java
@Override
@RequireAuth
public void handle(TdApi.Message message) {
    Long chatId = getChatId(message);
    String[] args = getArgs(message);
    String noSpk = args[1];

    billsRepository.findByNoSpk(noSpk)
        .switchIfEmpty(Mono.error(new RuntimeException("SPK tidak ditemukan")))
        .zipWith(
            payingRepository.findByNoSpk(noSpk).defaultIfEmpty(new Paying())
        )
        .map(tuple -> formatDetail(tuple.getT1(), tuple.getT2()))
        .onErrorReturn("❌ SPK tidak ditemukan: " + noSpk)
        .subscribe(text -> messageService.sendMessage(chatId, text));
}
```

### Pattern: Search by name → pagination list
```java
billsRepository.findByNameContainingIgnoreCaseAndBranch(name, branch)
    .skip((long) page * PAGE_SIZE)
    .take(PAGE_SIZE)
    .collectList()
    .zipWith(
        billsRepository.countByBranchAndPayDown(branch, false)
    )
    .subscribe(tuple -> {
        List<Bills> items = tuple.getT1();
        Long total = tuple.getT2();
        String text = formatList(items, page, total);
        TdApi.ReplyMarkupInlineKeyboard keyboard = buildPaginationKeyboard(page, total);
        messageService.sendMessage(chatId, text, keyboard);
    });
```

---

## Anti-Pattern yang Harus Dihindari

```java
// ❌ JANGAN: blok thread dengan .block()
Bills bills = billsRepository.findByNoSpk(noSpk).block();

// ✅ BOLEH (hanya di test atau startup one-time init)
Bills bills = billsRepository.findByNoSpk(noSpk).block(Duration.ofSeconds(5));

// ❌ JANGAN: nested subscribe
userRepository.findById(chatId).subscribe(user -> {
    billsRepository.findByAccountOfficer(user.getUserCode()).subscribe(bills -> {
        // ini "callback hell" versi reactive
    });
});

// ✅ GUNAKAN flatMap untuk chain
userRepository.findById(chatId)
    .flatMap(user -> billsRepository.findByAccountOfficer(user.getUserCode()))
    .subscribe(bills -> process(bills));

// ❌ JANGAN: abaikan error
mono.subscribe(value -> process(value));

// ✅ SELALU handle error
mono.subscribe(
    value -> process(value),
    error -> log.error("Error: {}", error.getMessage())
);
```
