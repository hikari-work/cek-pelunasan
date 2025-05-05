package org.cekpelunasan.utils;

import org.cekpelunasan.entity.Repayment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.WeakHashMap;

@Component
public class PenaltyUtils {
    private static final String DG_PRODUCT_SUFFIX = "DG";
    private static final String FLM_PRODUCT_SUFFIX = "FLM";
    private static final int YEAR_2025 = 2025;
    private static final int DEFAULT_MULTIPLIER = 0;
    private static final int DG_MULTIPLIER = 1;
    private static final int FLM_HIGH_MULTIPLIER = 3;
    private static final int FLM_LOW_MULTIPLIER = 6;
    private static final int HIGH_PENALTY = 2;
    private static final int LOW_PENALTY = 1;

    public Map<String, Long> penalty(String startDate, Long amount, String product, Repayment repayment) {
        LocalDateTime start = DateUtils.converterDate(startDate);
        long monthsBetween = calculateMonthsBetween(start);
        int year = extractYear(repayment);
        
        int multiplier = calculateMultiplier(product, monthsBetween, year);
        int penalty = calculatePenalty(multiplier);
        
        return createPenaltyMap(multiplier, amount * penalty);
    }

    private long calculateMonthsBetween(LocalDateTime start) {
        return ChronoUnit.MONTHS.between(start, LocalDateTime.now());
    }

    private int extractYear(Repayment repayment) {
        return Integer.parseInt(repayment.getStartDate().substring(0, 4));
    }

    private int calculateMultiplier(String product, long monthsBetween, int year) {
        if (product.endsWith(DG_PRODUCT_SUFFIX)) {
            return DG_MULTIPLIER;
        }
        if (product.endsWith(FLM_PRODUCT_SUFFIX)) {
            return calculateFlmMultiplier(monthsBetween, year);
        }
        return DEFAULT_MULTIPLIER;
    }

    private int calculateFlmMultiplier(long monthsBetween, int year) {
        long threshold = (year == YEAR_2025) ? 11 : 12;
        return monthsBetween >= threshold ? FLM_HIGH_MULTIPLIER : FLM_LOW_MULTIPLIER;
    }

    private int calculatePenalty(int multiplier) {
        return multiplier == FLM_LOW_MULTIPLIER ? HIGH_PENALTY : LOW_PENALTY;
    }

    private Map<String, Long> createPenaltyMap(int multiplier, long penaltyAmount) {
        Map<String, Long> map = new WeakHashMap<>();
        map.put("multiplier", (long) multiplier);
        map.put("penalty", penaltyAmount);
        return map;
    }
}