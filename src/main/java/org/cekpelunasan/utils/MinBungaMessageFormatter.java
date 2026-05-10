package org.cekpelunasan.utils;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.core.entity.Bills;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MinBungaMessageFormatter {

    private final RupiahFormatUtils rupiahFormatUtils;

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final Locale ID = new Locale("id", "ID");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", ID);
    private static final DateTimeFormatter DATE_WITH_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID);
    private static final int MAX_MSG_CHARS = 3800;

    public List<String> format(List<Bills> allBills, List<String> selectedDatesStr, String identifier) {
        LocalDate today = LocalDate.now(WIB);

        List<LocalDate> sortedDates = selectedDatesStr.stream()
            .map(LocalDate::parse)
            .sorted()
            .toList();

        Set<String> alreadyShown = new HashSet<>();
        List<String> messages = new ArrayList<>();

        for (LocalDate date : sortedDates) {
            int daysDiff = (int) ChronoUnit.DAYS.between(today, date);
            int maxDayLateForSection = 90 - daysDiff;

            List<Bills> forThisDate = allBills.stream()
                .filter(b -> parseDayLate(b.getDayLate()) + daysDiff >= 90)
                .filter(b -> !alreadyShown.contains(b.getNoSpk()))
                .toList();

            if (forThisDate.isEmpty()) continue;

            alreadyShown.addAll(forThisDate.stream().map(Bills::getNoSpk).toList());

            String header = buildSectionHeader(today, date, daysDiff, maxDayLateForSection, identifier, forThisDate.size());
            StringBuilder current = new StringBuilder(header);

            for (Bills bill : forThisDate) {
                String entry = buildBillEntry(bill, today);
                if (current.length() + entry.length() > MAX_MSG_CHARS) {
                    messages.add(current.toString());
                    current = new StringBuilder("_Lanjutan " + date.format(DATE_FORMAT) + "_\n\n");
                }
                current.append(entry);
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

    private String buildSectionHeader(LocalDate today, LocalDate date, int daysDiff, int maxDayLateForSection, String identifier, int count) {
        LocalDate batas90Hari = today.plusDays(90 - maxDayLateForSection);
        return "*Tagihan: " + date.format(DATE_FORMAT) + "* (+" + daysDiff + " hari)\n" +
            "Minimal bayar Maksimal di: " + batas90Hari.format(DATE_WITH_DAY) + "\n" +
            "ID: " + identifier + " | Jumlah: " + count + " tagihan\n" +
            "─────────────────────\n\n";
    }

    private String buildBillEntry(Bills bill, LocalDate today) {
        int dayLate = parseDayLate(bill.getDayLate());
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

    private int parseDayLate(String dayLate) {
        if (dayLate == null || dayLate.isBlank()) return 0;
        try {
            return Integer.parseInt(dayLate.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
