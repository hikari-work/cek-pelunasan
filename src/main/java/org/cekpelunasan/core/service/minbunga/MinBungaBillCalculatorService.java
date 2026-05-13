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
        List<DatedBill> dated = allBills.stream().map(DatedBill::of).toList();
        Set<String> alreadyShown = new HashSet<>();
        List<BillsForDate> result = new ArrayList<>();

        for (LocalDate date : targetDates.stream().sorted().toList()) {
            int daysDiff = (int) ChronoUnit.DAYS.between(today, date);
            List<DatedBill> forThisDate = dated.stream()
                .filter(db -> db.dayLate() + daysDiff >= 90)
                .filter(db -> !alreadyShown.contains(db.bill().getNoSpk()))
                .toList();
            if (forThisDate.isEmpty()) continue;
            alreadyShown.addAll(forThisDate.stream().map(db -> db.bill().getNoSpk()).toList());
            result.add(new BillsForDate(date, daysDiff, forThisDate));
        }
        return result;
    }

    /**
     * Menghitung batas bawah dayLate yang masih bisa tembus 90 hari di salah satu tanggal target.
     * Bill dengan dayLate di bawah threshold ini tidak mungkin lolos filter {@link #calculate},
     * jadi tidak perlu diambil dari database.
     *
     * @param targetDates daftar tanggal target penagihan
     * @return threshold {@code minDayLate} (inklusif) untuk query ke DB; minimal 0
     */
    public int minDayLateThreshold(List<LocalDate> targetDates) {
        LocalDate today = LocalDate.now(WIB);
        int maxDaysDiff = targetDates.stream()
            .mapToInt(d -> (int) ChronoUnit.DAYS.between(today, d))
            .max()
            .orElse(0);
        return Math.max(0, 90 - maxDaysDiff);
    }
}
