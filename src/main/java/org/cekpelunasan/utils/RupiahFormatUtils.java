package org.cekpelunasan.utils;

import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

@Component
public class RupiahFormatUtils {

    public String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }

}
