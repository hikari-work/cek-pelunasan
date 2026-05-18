package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.KolekTas;
import org.cekpelunasan.core.repository.KolekTasRepository;
import org.cekpelunasan.miniapp.dto.KolekTasSummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * API pencarian KolekTas (daftar kelompok penagihan) untuk Mini App.
 *
 * <p>User memilih nama kelompok, lalu semua anggota kelompok tersebut
 * ditampilkan sekaligus tanpa paginasi.</p>
 */
@RestController
@RequestMapping("/api/mini/kolektas")
@RequiredArgsConstructor
public class MiniAppKolekTasController {

    private final KolekTasRepository kolekTasRepository;

    @GetMapping("/search")
    public Mono<ResponseEntity<List<KolekTasSummaryDTO>>> search(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        return kolekTasRepository.findByKelompokIgnoreCase(q.trim())
                .map(this::toDTO)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<KolekTasSummaryDTO>> detail(@PathVariable String id) {
        return kolekTasRepository.findById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().<KolekTasSummaryDTO>build());
    }

    private KolekTasSummaryDTO toDTO(KolekTas k) {
        return new KolekTasSummaryDTO(
                k.getId(),
                k.getKelompok(),
                k.getKantor(),
                k.getRekening(),
                k.getNama(),
                k.getAlamat(),
                k.getNoHp(),
                k.getKolek(),
                k.getNominal(),
                k.getAccountOfficer(),
                k.getCif()
        );
    }
}
