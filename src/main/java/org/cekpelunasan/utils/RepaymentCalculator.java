package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Map;

public class RepaymentCalculator {

    public String calculate(Repayment repayment, Map<String, Long> penaltyMap) {
        Long bakidebet = repayment.getAmount();
        Long tunggakan = repayment.getInterest();
        Long denda = repayment.getPenaltyRepayment();
        Long total = bakidebet + tunggakan + denda + penaltyMap.get("penalty");

        // Bagian Informasi

        // Bagian Tagihan


        return "📄 *Informasi Nasabah*\n" +
                "No SPK\t\t\t: " + formatText(repayment.getCustomerId()) + "\n" +
                "Nama\t\t\t\t\t: " + formatText(repayment.getName()) + "\n" +
                "Alamat\t\t\t: " + formatText(repayment.getAddress()) + "\n" +
                "Produk\t\t\t: " + formatText(repayment.getProduct()) + "\n" +
                "Plafond\t\t\t: " + formatRupiah(repayment.getPlafond()) + "\n\n" +

// Bagian Tagihan
                "💰 *Rincian Tagihan*\n" +
                "Baki Debet\t\t: " + formatRupiah(bakidebet) + "\n" +
                "Tunggakan\t\t: " + formatRupiah(tunggakan) + "\n" +
                "Penalty +" + penaltyMap.get("multiplier") + "\t\t: " + formatRupiah(penaltyMap.get("penalty")) + "\n" +
                "Denda\t\t\t: " + formatRupiah(denda) + "\n" +
                "Total\t\t\t: *" + formatRupiah(total) + "*";
    }

    private String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }

    private String formatText(String text) {
        return text == null ? "-" : text;
    }
}
