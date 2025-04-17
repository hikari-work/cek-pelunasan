package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

public class FindByNameUtils {

    public String findByNameToStringMessage(List<Repayment> repayments) {
        if (repayments.isEmpty()) {
            return "Pencarianan tidak ditemukan";
        }
        StringBuilder result = new StringBuilder();
        int i = 1;
        for (Repayment repayment : repayments) {
            result.append("📄 *").append(i + 1).append(". Informasi SPK*\n");
            result.append("🔢 *No SPK*     : `").append(repayment.getCustomerId()).append("`\n");
            result.append("👤 *Nama*        : ").append(repayment.getName()).append("\n");
            result.append("🏡 *Alamat*      : ").append(repayment.getAddress()).append("\n");
            result.append("💰 *Plafond*     : ").append(formatRupiah(repayment.getPlafond())).append("\n\n");

        }
        if (result.toString().length() > 4096) {
            result = new StringBuilder(result.substring(0, 4096));
            result.append("...");
        }
        return result.toString();
    }
    private String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }
}
