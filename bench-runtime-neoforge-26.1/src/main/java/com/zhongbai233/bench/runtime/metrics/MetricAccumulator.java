package com.zhongbai233.bench.runtime.metrics;

import java.util.Arrays;

/**
 * Fixed-capacity sample buffer for one metric in one phase.
 *
 * <p>The record path is a bounds check and an array store, so tick and frame hot paths never
 * allocate. Samples past the capacity are counted as dropped instead of resizing.
 */
public final class MetricAccumulator {
    private final double[] values;
    private int size;
    private long dropped;

    public MetricAccumulator(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        values = new double[capacity];
    }

    public void record(double value) {
        if (size < values.length) {
            values[size++] = value;
        } else {
            dropped++;
        }
    }

    public int sampleCount() {
        return size;
    }

    public long droppedCount() {
        return dropped;
    }

    /** Copy of the recorded samples, oldest first. */
    public double[] copyValues() {
        return Arrays.copyOf(values, size);
    }

    public MetricSummary summarize() {
        return MetricSummary.of(values, size, dropped);
    }
}
