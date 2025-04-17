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


        return """
📄 *Informasi Nasabah*
───────────────
🆔 *No SPK*        : `%s`
👤 *Nama*           : %s
🏡 *Alamat*         : %s
📦 *Produk*         : %s
💸 *Plafond*        : %s

💰 *Rincian Tagihan*
───────────────
🏦 *Baki Debet*     : %s
📉 *Tunggakan*      : %s
⏱ *Penalty +%s*     : %s
⚠️ *Denda*           : %s
💳 *Total Tagihan*  : *%s*
""".formatted(
                formatText(repayment.getCustomerId().toString()),
                formatText(repayment.getName()),
                formatText(repayment.getAddress()),
                formatText(repayment.getProduct()),
                formatRupiah(repayment.getPlafond()),

                formatRupiah(bakidebet),
                formatRupiah(tunggakan),
                penaltyMap.get("multiplier"),
                formatRupiah(penaltyMap.get("penalty")),
                formatRupiah(denda),
                formatRupiah(total)
        );

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
