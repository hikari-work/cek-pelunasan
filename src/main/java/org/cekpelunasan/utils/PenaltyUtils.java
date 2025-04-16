package org.cekpelunasan.utils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.WeakHashMap;

public class PenaltyUtils {

    public Map<String, Long> penalty(String startDate, Long amount, String product) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = DateUtils.converterDate(startDate);
        long monthsBetween = ChronoUnit.MONTHS.between(start, now);

        int multiplier = product.endsWith("DG") ? 1 :
                product.endsWith("FLM") ? (monthsBetween >= 12 ? 3 : 6) : 0;

        int penalty;
        if (multiplier == 6) {
            penalty = 2;
        } else {
            penalty = 1;
        }
        Map<String, Long> map = new WeakHashMap<>();
        map.put("multiplier", Long.parseLong(String.valueOf(multiplier)));
        map.put("penalty", amount * penalty);
        return map;

    }
}
