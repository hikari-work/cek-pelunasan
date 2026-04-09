package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.miniapp.dto.TagihanSummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * API pencarian dan detail tagihan untuk Mini App.
 *
 * <p>Logika deteksi input:
 * <ul>
 *   <li>Numerik (hanya angka): cari sebagai no SPK → {@code getBillById}</li>
 *   <li>Lainnya: cari sebagai nama nasabah (lintas semua cabang)</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/mini/tagihan")
@RequiredArgsConstructor
public class MiniAppTagihanController {

    private final BillService billService;

    @GetMapping("/search")
    public Mono<ResponseEntity<List<TagihanSummaryDTO>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page
    ) {
        if (q == null || q.isBlank()) {
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        String query = q.trim();

        if (query.matches("\\d+")) {
            return billService.getBillById(query)
                    .map(bill -> List.of(toSummary(bill)))
                    .defaultIfEmpty(List.of())
                    .map(ResponseEntity::ok);
        }

        return billService.findByName(query, page, 20)
                .map(p -> p.getContent().stream().map(this::toSummary).toList())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{spk}")
    public Mono<ResponseEntity<Bills>> detail(@PathVariable String spk) {
        return billService.getBillById(spk)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().<Bills>build());
    }

    private TagihanSummaryDTO toSummary(Bills b) {
        return new TagihanSummaryDTO(
                b.getNoSpk(), b.getName(), b.getBranch(), b.getProduct(),
                b.getCollectStatus(), b.getDayLate(), b.getInstallment(), b.getFullPayment()
        );
    }
}
