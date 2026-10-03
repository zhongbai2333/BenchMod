package com.zhongbai233.bench.runtime.metrics;

import java.util.Arrays;

/** Order statistics and moments computed once from an accumulator, after the hot path ended. */
public record MetricSummary(
        long count,
        long dropped,
        double min,
        double max,
        double mean,
        double median,
        double p90,
        double p95,
        double p99,
        double stdDev) {

    static MetricSummary of(double[] values, int size, long dropped) {
        if (size == 0) {
            return new MetricSummary(0, dropped, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        double total = 0.0;
        for (int i = 0; i < size; i++) total += sorted[i];
        double mean = total / size;
        double squaredError = 0.0;
        for (int i = 0; i < size; i++) {
            double delta = sorted[i] - mean;
            squaredError += delta * delta;
        }
        return new MetricSummary(
                size,
                dropped,
                sorted[0],
                sorted[size - 1],
                mean,
                percentileOf(sorted, 50.0),
                percentileOf(sorted, 90.0),
                percentileOf(sorted, 95.0),
                percentileOf(sorted, 99.0),
                Math.sqrt(squaredError / size));
    }

    /** Nearest-rank percentile over a sorted array, matching the frame sampler's convention. */
    static double percentileOf(double[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}
