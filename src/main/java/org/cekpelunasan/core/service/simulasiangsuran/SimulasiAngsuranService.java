package org.cekpelunasan.core.service.simulasiangsuran;

import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.simulasiangsuran.SimulasiAngsuranResult;
import org.cekpelunasan.core.entity.simulasiangsuran.SkenarioDetail;
import org.cekpelunasan.core.entity.simulasiangsuran.TahapPembayaran;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
public class SimulasiAngsuranService {

    private static final ZoneOffset WIB = ZoneOffset.ofHours(7);

    /**
     * Menghitung tiga skenario pembayaran minimal dan menentukan rekomendasi
     * berdasarkan total biaya terkecil.
     *
     * @param bill data tagihan nasabah
     * @return hasil simulasi berisi tiga skenario dan rekomendasi
     */
    public SimulasiAngsuranResult hitung(Bills bill) {
        int currentDayLate = parseDayLate(bill.getDayLate());
        LocalDate today = LocalDate.now(WIB);

        BigDecimal tunggakanBunga = toBigDecimal(bill.getLastInterest());
        BigDecimal minimalPokok   = toBigDecimal(bill.getMinPrincipal());
        BigDecimal minimalBunga   = toBigDecimal(bill.getMinInterest());

        // Tanggal saat dayLate mencapai 91 hari (non-performing threshold)
        long daysUntil91 = 91L - currentDayLate;
        LocalDate dateAt91 = today.plusDays(Math.max(daysUntil91, 0));

        SkenarioDetail skenarioA = hitungSkenarioA(tunggakanBunga, minimalPokok, today);
        SkenarioDetail skenarioB = hitungSkenarioB(minimalPokok, dateAt91, daysUntil91);
        SkenarioDetail skenarioC = hitungSkenarioC(minimalBunga, minimalPokok, today, dateAt91);

        List<SkenarioDetail> skenarioList = List.of(skenarioA, skenarioB, skenarioC);
        String rekomendasi = tentukanRekomendasi(skenarioList);
        BigDecimal minimum = skenarioList.stream()
            .filter(s -> s.kode().equals(rekomendasi))
            .map(SkenarioDetail::totalBayar)
            .findFirst()
            .orElse(BigDecimal.ZERO);

        return SimulasiAngsuranResult.builder()
            .rekomendasiSkenario(rekomendasi)
            .totalBayarMinimum(minimum)
            .skenarioList(skenarioList)
            .build();
    }

    /**
     * Skenario A: Bayar hari ini saat masih performing (dayLate ≤ 90).
     * Alokasi: bunga dulu, sisa ke pokok.
     */
    private SkenarioDetail hitungSkenarioA(BigDecimal tunggakanBunga, BigDecimal minimalPokok,
                                            LocalDate today) {
        BigDecimal total = tunggakanBunga.add(minimalPokok);
        TahapPembayaran tahap = new TahapPembayaran(today, total, minimalPokok, tunggakanBunga);
        return new SkenarioDetail(
            "A",
            "Bayar Hari Ini (Full Performing)",
            total,
            List.of(tahap),
            "Bayar seluruh tunggakan bunga + minimal pokok sekarang."
        );
    }

    /**
     * Skenario B: Tunggu sampai dayLate mencapai 91 hari (non-performing).
     * Alokasi: pokok dulu. Hanya bayar minimal pokok.
     */
    private SkenarioDetail hitungSkenarioB(BigDecimal minimalPokok, LocalDate dateAt91,
                                            long daysUntil91) {
        TahapPembayaran tahap = new TahapPembayaran(dateAt91, minimalPokok, minimalPokok, BigDecimal.ZERO);
        String ket = daysUntil91 > 0
            ? "Bayar minimal pokok pada hari ke-91 (" + daysUntil91 + " hari lagi). Tidak perlu bayar bunga."
            : "Sudah melewati 91 hari — bayar minimal pokok sekarang.";
        return new SkenarioDetail(
            "B",
            "Tunggu 91 Hari (Full Non-Performing)",
            minimalPokok,
            List.of(tahap),
            ket
        );
    }

    /**
     * Skenario C: Hybrid — bayar minimal bunga hari ini (performing),
     * lalu bayar minimal pokok saat non-performing (hari ke-91).
     */
    private SkenarioDetail hitungSkenarioC(BigDecimal minimalBunga, BigDecimal minimalPokok,
                                            LocalDate today, LocalDate dateAt91) {
        BigDecimal total = minimalBunga.add(minimalPokok);
        TahapPembayaran tahap1 = new TahapPembayaran(today, minimalBunga, BigDecimal.ZERO, minimalBunga);
        TahapPembayaran tahap2 = new TahapPembayaran(dateAt91, minimalPokok, minimalPokok, BigDecimal.ZERO);
        return new SkenarioDetail(
            "C",
            "Hybrid (Dua Tahap)",
            total,
            List.of(tahap1, tahap2),
            "Bayar minimal bunga hari ini, lalu minimal pokok saat hari ke-91."
        );
    }

    /**
     * Menentukan skenario dengan total bayar terkecil sebagai rekomendasi.
     */
    private String tentukanRekomendasi(List<SkenarioDetail> skenarioList) {
        return skenarioList.stream()
            .min(Comparator.comparing(SkenarioDetail::totalBayar))
            .map(SkenarioDetail::kode)
            .orElse("B");
    }

    private int parseDayLate(String dayLate) {
        if (dayLate == null || dayLate.isBlank()) return 0;
        try {
            return Integer.parseInt(dayLate.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal toBigDecimal(Long value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }
}
