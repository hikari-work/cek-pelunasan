package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.miniapp.dto.PelunasanDetailDTO;
import org.cekpelunasan.miniapp.dto.TagihanSummaryDTO;
import org.cekpelunasan.platform.whatsapp.service.dto.PelunasanDto;
import org.cekpelunasan.platform.whatsapp.service.pelunasan.PelunasanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * API pencarian dan detail pelunasan untuk Mini App.
 *
 * <p>Search menggunakan data Bills (cari by nama atau no SPK).
 * Detail menghitung total pelunasan menggunakan {@link PelunasanService} dengan rumus:
 * <b>Baki Debet + Perhitungan Bunga + Penalty + Denda</b></p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mini/pelunasan")
@RequiredArgsConstructor
public class MiniAppPelunasanController {

    private final BillService billService;
    private final PelunasanService pelunasanService;

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
    public Mono<ResponseEntity<PelunasanDetailDTO>> detail(@PathVariable String spk) {
        return billService.getBillById(spk)
                .flatMap(bill -> Mono.fromCallable(() -> {
                    PelunasanDto dto = pelunasanService.calculatePelunasn(bill);
                    return toDetailDTO(dto);
                }).onErrorResume(e -> {
                    log.warn("Gagal kalkulasi pelunasan untuk SPK {}: {}", spk, e.getMessage());
                    return Mono.just(fallbackDetail(bill));
                }))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().<PelunasanDetailDTO>build());
    }

    private PelunasanDetailDTO toDetailDTO(PelunasanDto dto) {
        return new PelunasanDetailDTO(
                dto.getSpk(),
                dto.getNama(),
                dto.getAlamat(),
                null,
                dto.getTglRealisasi(),
                dto.getTglJatuhTempo(),
                dto.getRencanaPelunasan(),
                dto.getPlafond(),
                dto.getBakiDebet(),
                dto.getPerhitunganBunga(),
                dto.getTypeBunga(),
                dto.getPenalty(),
                dto.getMultiplierPenalty(),
                dto.getDenda(),
                dto.getTotalPelunasan()
        );
    }

    /** Fallback jika kalkulasi gagal (misal: format tanggal tidak valid) — tampilkan data dasar Bills. */
    private PelunasanDetailDTO fallbackDetail(Bills bill) {
        return new PelunasanDetailDTO(
                bill.getNoSpk(),
                bill.getName(),
                bill.getAddress(),
                bill.getProduct(),
                bill.getRealization(),
                bill.getDueDate(),
                null,
                bill.getPlafond(),
                bill.getDebitTray(),
                null,
                null,
                null,
                null,
                null,
                bill.getFullPayment()
        );
    }

    private TagihanSummaryDTO toSummary(Bills b) {
        return new TagihanSummaryDTO(
                b.getNoSpk(), b.getName(), b.getBranch(), b.getProduct(),
                b.getCollectStatus(), b.getDayLate(), b.getInstallment(), b.getFullPayment()
        );
    }
}
