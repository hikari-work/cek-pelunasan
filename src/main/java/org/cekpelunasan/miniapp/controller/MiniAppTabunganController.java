package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.miniapp.dto.TabunganSummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * API pencarian dan detail tabungan untuk Mini App.
 *
 * <p>Logika deteksi input:
 * <ul>
 *   <li>Numerik (hanya angka): cari sebagai nomor rekening (tabId)</li>
 *   <li>Lainnya: cari sebagai nama nasabah (lintas semua cabang, limit 20)</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/mini/tabungan")
@RequiredArgsConstructor
public class MiniAppTabunganController {

    private final SavingsService savingsService;

    @GetMapping("/search")
    public Mono<ResponseEntity<List<TabunganSummaryDTO>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page
    ) {
        if (q == null || q.isBlank()) {
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        String query = q.trim();

        if (query.matches("\\d+")) {
            return savingsService.findById(query)
                    .map(s -> List.of(toSummary(s)))
                    .defaultIfEmpty(List.of())
                    .map(ResponseEntity::ok);
        }

        return savingsService.findByName(query, 0)
                .map(this::toSummary)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{tabId}")
    public Mono<ResponseEntity<Savings>> detail(@PathVariable String tabId) {
        return savingsService.findById(tabId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().<Savings>build());
    }

    private TabunganSummaryDTO toSummary(Savings s) {
        return new TabunganSummaryDTO(s.getTabId(), s.getName(), s.getBranch(), s.getType(), s.getBalance());
    }
}
