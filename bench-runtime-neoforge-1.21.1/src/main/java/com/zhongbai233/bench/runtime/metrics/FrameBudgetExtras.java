package com.zhongbai233.bench.runtime.metrics;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Frame-interval statistics beyond plain percentiles: percentile-low means and over-budget counts.
 *
 * <p>"1% low" is the mean of the slowest 1% of intervals, the number players experience as stutter
 * even when the overall mean looks fine.
 */
public final class FrameBudgetExtras {
    private static final long BUDGET_60_FPS_NANOS = 16_666_667L;
    private static final long BUDGET_30_FPS_NANOS = 33_333_334L;
    private static final long BUDGET_20_FPS_NANOS = 50_000_000L;
    private static final long BUDGET_10_FPS_NANOS = 100_000_000L;

    private FrameBudgetExtras() {}

    public static Map<String, Long> compute(double[] intervalsNanos) {
        Map<String, Long> extras = new LinkedHashMap<>();
        if (intervalsNanos.length == 0) return extras;
        double[] sorted = Arrays.copyOf(intervalsNanos, intervalsNanos.length);
        Arrays.sort(sorted);
        extras.put("low_1pct_mean_ns", lowPercentMean(sorted, 0.01));
        extras.put("low_0_1pct_mean_ns", lowPercentMean(sorted, 0.001));
        extras.put("over_16_67ms_frames", countOver(sorted, BUDGET_60_FPS_NANOS));
        extras.put("over_33_33ms_frames", countOver(sorted, BUDGET_30_FPS_NANOS));
        extras.put("over_50ms_frames", countOver(sorted, BUDGET_20_FPS_NANOS));
        extras.put("over_100ms_frames", countOver(sorted, BUDGET_10_FPS_NANOS));
        return extras;
    }

    /** Mean of the worst {@code fraction} of intervals; at least one sample is always included. */
    private static long lowPercentMean(double[] sorted, double fraction) {
        int worst = Math.max(1, (int) Math.ceil(sorted.length * fraction));
        double total = 0.0;
        for (int i = sorted.length - worst; i < sorted.length; i++) total += sorted[i];
        return Math.round(total / worst);
    }

    private static long countOver(double[] sorted, long budgetNanos) {
        int count = 0;
        for (int i = sorted.length - 1; i >= 0 && sorted[i] > budgetNanos; i--) count++;
        return count;
    }
}
