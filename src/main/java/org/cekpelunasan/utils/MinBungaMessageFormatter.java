package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.cekpelunasan.core.service.minbunga.BillsForDate;
import org.cekpelunasan.core.service.minbunga.DatedBill;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MinBungaMessageFormatter {

    private final RupiahFormatUtils rupiahFormatUtils;

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final Locale ID = new Locale("id", "ID");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", ID);
    private static final DateTimeFormatter DATE_WITH_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID);
    private static final int MAX_MSG_CHARS = 3800;

    public List<String> format(List<BillsForDate> billsByDate, String identifier) {
        LocalDate today = LocalDate.now(WIB);
        List<String> messages = new ArrayList<>();

        for (BillsForDate entry : billsByDate) {
            LocalDate date = entry.targetDate();
            int daysDiff = entry.daysDiff();
            List<DatedBill> forThisDate = entry.bills();

            String header = buildSectionHeader(date, daysDiff, identifier, forThisDate.size());
            StringBuilder current = new StringBuilder(header);

            for (DatedBill db : forThisDate) {
                String entryStr = buildBillEntry(db, today);
                if (current.length() + entryStr.length() > MAX_MSG_CHARS) {
                    messages.add(current.toString());
                    current = new StringBuilder("_Lanjutan " + date.format(DATE_FORMAT) + "_\n\n");
                }
                current.append(entryStr);
            }

            if (!current.isEmpty()) {
                messages.add(current.toString());
            }
        }

        if (messages.isEmpty()) {
            messages.add("*Tidak ada tagihan yang memenuhi kriteria.*\n_Semua nasabah masih aman dalam batas DayLate 90 hari._");
        }

        return messages;
    }

    private String buildSectionHeader(LocalDate date, int daysDiff, String identifier, int count) {
        return "*Tagihan: " + date.format(DATE_FORMAT) + "* (+" + daysDiff + " hari)\n" +
            "Minimal bayar Maksimal di: " + date.format(DATE_WITH_DAY) + "\n" +
            "ID: " + identifier + " | Jumlah: " + count + " tagihan\n" +
            "─────────────────────\n\n";
    }

    private String buildBillEntry(DatedBill db, LocalDate today) {
        Bills bill = db.bill();
        int dayLate = db.dayLate();
        LocalDate maksBayar = today.plusDays(Math.max(0, 90 - dayLate));
        long jikaNotPay = nullSafe(bill.getLastPrincipal()) + nullSafe(bill.getPrincipal()) + nullSafe(bill.getMinInterest());

        return "*" + bill.getName() + "*\n" +
            "Alamat: " + bill.getAddress() + "\n" +
            "AO: " + bill.getAccountOfficer() + "\n\n" +
            "Plafond: " + rupiahFormatUtils.formatRupiah(nullSafe(bill.getPlafond())) + "\n" +
            "Baki Debet: " + rupiahFormatUtils.formatRupiah(nullSafe(bill.getDebitTray())) + "\n" +
            "Tgg. Pokok: " + rupiahFormatUtils.formatRupiah(nullSafe(bill.getLastPrincipal())) + "\n" +
            "Tgg. Bunga: " + rupiahFormatUtils.formatRupiah(nullSafe(bill.getLastInterest())) + "\n" +
            "Min. Pokok: " + rupiahFormatUtils.formatRupiah(nullSafe(bill.getMinPrincipal())) + "\n" +
            "Min. Bunga: " + rupiahFormatUtils.formatRupiah(nullSafe(bill.getMinInterest())) + "\n\n" +
            "Maks. Bayar: " + maksBayar.format(DATE_WITH_DAY) + "\n" +
            "Jika Tdk Bayar: " + rupiahFormatUtils.formatRupiah(jikaNotPay) + "\n" +
            "─────────────────────\n\n";
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
