package com.zhongbai233.bench.runtime.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FrameBudgetExtrasTest {
    @Test
    void computesPercentileLowsAndBudgetCounts() {
        double[] intervals = new double[200];
        for (int i = 0; i < 198; i++) intervals[i] = 8_000_000.0;
        intervals[198] = 40_000_000.0;
        intervals[199] = 120_000_000.0;

        Map<String, Long> extras = FrameBudgetExtras.compute(intervals);

        // Worst 1% of 200 samples is the two slowest frames.
        assertEquals(80_000_000L, extras.get("low_1pct_mean_ns"));
        assertEquals(120_000_000L, extras.get("low_0_1pct_mean_ns"));
        assertEquals(2L, extras.get("over_16_67ms_frames"));
        assertEquals(2L, extras.get("over_33_33ms_frames"));
        assertEquals(1L, extras.get("over_50ms_frames"));
        assertEquals(1L, extras.get("over_100ms_frames"));
    }

    @Test
    void uniformFastFramesHaveNoOverBudgetFrames() {
        double[] intervals = new double[50];
        java.util.Arrays.fill(intervals, 4_000_000.0);

        Map<String, Long> extras = FrameBudgetExtras.compute(intervals);
        assertEquals(4_000_000L, extras.get("low_1pct_mean_ns"));
        assertEquals(0L, extras.get("over_16_67ms_frames"));
        assertEquals(0L, extras.get("over_100ms_frames"));
    }

    @Test
    void emptyInputYieldsNoExtras() {
        assertTrue(FrameBudgetExtras.compute(new double[0]).isEmpty());
    }
}
