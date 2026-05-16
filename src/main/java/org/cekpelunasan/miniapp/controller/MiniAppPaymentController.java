package org.cekpelunasan.miniapp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.PaymentDetails;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.paymentdetails.PaymentDetailsService;
import org.cekpelunasan.miniapp.dto.PaymentDetailDTO;
import org.cekpelunasan.miniapp.dto.PaymentRowDTO;
import org.cekpelunasan.miniapp.dto.TagihanSummaryDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * API pencarian dan detail Payment untuk Mini App.
 *
 * <p>Search memakai data Bills (sama dengan Tagihan/Pelunasan).
 * Detail menampilkan riwayat angsuran nasabah dari koleksi {@code payment_details}
 * untuk satu nomor SPK, plus snapshot tunggakan dan pembayaran minimum dari Bills.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mini/payment")
@RequiredArgsConstructor
public class MiniAppPaymentController {

    private final BillService billService;
    private final PaymentDetailsService paymentDetailsService;

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
    public Mono<ResponseEntity<PaymentDetailDTO>> detail(@PathVariable String spk) {
        Mono<Bills> billMono = billService.getBillById(spk);
        Mono<List<PaymentDetails>> rowsMono = paymentDetailsService.findByNoSpk(spk).collectList();

        return billMono
                .zipWith(rowsMono)
                .map(tuple -> toDetailDTO(tuple.getT1(), tuple.getT2()))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private PaymentDetailDTO toDetailDTO(Bills bill, List<PaymentDetails> records) {
        List<PaymentRowDTO> rows = new ArrayList<>(records.size());
        int idx = 1;
        for (PaymentDetails pd : records) {
            String type = pd.getKodePosting() == null ? "" : pd.getKodePosting().trim().toUpperCase();
            long nominal = pd.getNominalAngsuran() == null ? 0L : pd.getNominalAngsuran();
            long denda = pd.getDenda() == null ? 0L : pd.getDenda();
            long penalti = pd.getPenalti() == null ? 0L : pd.getPenalti();
            long pokok = "P".equals(type) ? nominal : 0L;
            long bunga = "I".equals(type) ? nominal : 0L;
            long total = nominal + denda + penalti;
            boolean highlight = denda + penalti > 0L;

            rows.add(new PaymentRowDTO(idx++, pd.getTanggal(), type, pokok, bunga, denda, penalti, total, highlight));
        }

        return new PaymentDetailDTO(
                bill.getNoSpk(),
                bill.getName(),
                bill.getBranch(),
                bill.getProduct(),
                rows,
                nz(bill.getLastPrincipal()),
                nz(bill.getLastInterest()),
                nz(bill.getMinPrincipal()),
                nz(bill.getMinInterest())
        );
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }

    private TagihanSummaryDTO toSummary(Bills b) {
        return new TagihanSummaryDTO(
                b.getNoSpk(), b.getName(), b.getBranch(), b.getProduct(),
                b.getCollectStatus(), b.getDayLate(), b.getInstallment(), b.getFullPayment(),
                b.getCkpnType(), b.getCkpnNominal(), b.getRekeningAutobedet()
        );
    }
}
