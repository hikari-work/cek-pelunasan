package org.cekpelunasan.core.service.minbunga;

import org.cekpelunasan.core.entity.Bills;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MinBungaBillCalculatorService {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    public List<BillsForDate> calculate(List<Bills> allBills, List<LocalDate> targetDates) {
        LocalDate today = LocalDate.now(WIB);
        Set<String> alreadyShown = new HashSet<>();
        List<BillsForDate> result = new ArrayList<>();

        for (LocalDate date : targetDates.stream().sorted().toList()) {
            int daysDiff = (int) ChronoUnit.DAYS.between(today, date);
            List<Bills> forThisDate = allBills.stream()
                .filter(b -> parseDayLate(b.getDayLate()) + daysDiff >= 90)
                .filter(b -> !alreadyShown.contains(b.getNoSpk()))
                .toList();
            if (forThisDate.isEmpty()) continue;
            alreadyShown.addAll(forThisDate.stream().map(Bills::getNoSpk).toList());
            result.add(new BillsForDate(date, daysDiff, forThisDate));
        }
        return result;
    }

    private static int parseDayLate(String dayLate) {
        if (dayLate == null || dayLate.isBlank()) return 0;
        try {
            return Integer.parseInt(dayLate.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
