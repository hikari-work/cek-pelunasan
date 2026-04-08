package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Savings;
import org.cekpelunasan.core.service.savings.SavingsService;
import org.cekpelunasan.miniapp.dto.CanvasSummaryDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API canvasing tabungan untuk Mini App.
 *
 * <p>Mencari nasabah tabungan berdasarkan kata kunci alamat, hanya mengembalikan
 * nasabah yang belum memiliki tagihan aktif (logika sama dengan /canvas di Telegram bot).
 * Kata kunci dipisahkan dengan koma atau spasi.</p>
 */
@RestController
@RequestMapping("/api/mini/canvas")
@RequiredArgsConstructor
public class MiniAppCanvasController {

    private final SavingsService savingsService;

    @GetMapping("/search")
    public Mono<ResponseEntity<List<CanvasSummaryDTO>>> search(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        List<String> keywords = Arrays.stream(q.trim().split(","))
                .flatMap(part -> Arrays.stream(part.trim().split("\\s+")))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (keywords.isEmpty()) {
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        return savingsService.findFilteredSavings(keywords, PageRequest.of(0, 500))
                .map(page -> page.getContent().stream().map(this::toSummary).toList())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{tabId}")
    public Mono<ResponseEntity<Savings>> detail(@PathVariable String tabId) {
        return savingsService.findById(tabId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().<Savings>build());
    }

    private CanvasSummaryDTO toSummary(Savings s) {
        return new CanvasSummaryDTO(
                s.getTabId(), s.getName(), s.getBranch(), s.getType(),
                s.getBalance(), s.getCif(), s.getAddress()
        );
    }
}
