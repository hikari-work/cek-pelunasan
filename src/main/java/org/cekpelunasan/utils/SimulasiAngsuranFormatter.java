package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.entity.simulasiangsuran.SimulasiAngsuranResult;
import org.cekpelunasan.core.entity.simulasiangsuran.SkenarioDetail;
import org.cekpelunasan.core.entity.simulasiangsuran.TahapPembayaran;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SimulasiAngsuranFormatter {

    private final RupiahFormatUtils rupiahFormatUtils;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("id", "ID"));

    public String format(SimulasiAngsuranResult result, Bills bill) {
        StringBuilder sb = new StringBuilder();

        sb.append("📊 *Simulasi Angsuran*\n");
        sb.append(String.format("SPK: `%s` — %s\n", bill.getNoSpk(), bill.getName()));
        sb.append(String.format("Hari Keterlambatan: *%s hari*\n", bill.getDayLate()));
        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n\n");

        for (SkenarioDetail skenario : result.getSkenarioList()) {
            sb.append(formatSkenario(skenario));
            sb.append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("✅ *Rekomendasi: Skenario %s*\n", result.getRekomendasiSkenario()));
        sb.append(String.format("Total Bayar Minimum: *%s*",
            rupiahFormatUtils.formatRupiah(result.getTotalBayarMinimum())));

        return sb.toString();
    }

    private String formatSkenario(SkenarioDetail skenario) {
        StringBuilder sb = new StringBuilder();

        String header = switch (skenario.kode()) {
            case "A" -> "🅰️ *Skenario A — " + skenario.namaSkenario() + "*";
            case "B" -> "🅱️ *Skenario B — " + skenario.namaSkenario() + "*";
            default  -> "🆚 *Skenario C — " + skenario.namaSkenario() + "*";
        };

        sb.append(header).append("\n");
        sb.append(String.format("💰 Total: *%s*\n", rupiahFormatUtils.formatRupiah(skenario.totalBayar())));

        for (TahapPembayaran tahap : skenario.tahapPembayaran()) {
            sb.append(formatTahap(tahap, skenario.tahapPembayaran().size() > 1));
        }

        sb.append(String.format("_%s_\n", skenario.keterangan()));
        return sb.toString();
    }

    private String formatTahap(TahapPembayaran tahap, boolean multiTahap) {
        StringBuilder sb = new StringBuilder();
        String tanggal = tahap.tanggal().format(DATE_FORMAT);

        if (multiTahap) {
            sb.append(String.format("  📅 *%s*\n", tanggal));
            sb.append(String.format("  Bayar: %s\n", rupiahFormatUtils.formatRupiah(tahap.jumlahBayar())));
        } else {
            sb.append(String.format("📅 Tanggal: %s\n", tanggal));
        }

        if (tahap.alokasiBunga().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("  • Bunga: %s\n", rupiahFormatUtils.formatRupiah(tahap.alokasiBunga())));
        }
        if (tahap.alokasiPokok().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(String.format("  • Pokok: %s\n", rupiahFormatUtils.formatRupiah(tahap.alokasiPokok())));
        }

        return sb.toString();
    }
}
