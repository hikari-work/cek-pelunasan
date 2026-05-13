package org.cekpelunasan.core.service.minbunga;

import org.cekpelunasan.core.entity.Bills;

public record DatedBill(Bills bill, int dayLate) {

    public static int parseDayLate(String dayLate) {
        if (dayLate == null || dayLate.isBlank()) return 0;
        try {
            return Integer.parseInt(dayLate.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static DatedBill of(Bills bill) {
        return new DatedBill(bill, parseDayLate(bill.getDayLate()));
    }
}
