package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.neoforge.client.BenchFrameMetrics;
import java.util.Arrays;

/** Fixed-capacity, allocation-free-on-frame-path interval sampler. */
public final class FrameIntervalSampler implements BenchFrameMetrics {
    private final long[] intervals;
    private int size;
    private long dropped;
    private long previousFrameNanos = -1;
    private long total;
    private long max;

    public FrameIntervalSampler(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        intervals = new long[capacity];
    }

    public void recordFrame(long timestampNanos) {
        if (previousFrameNanos >= 0) {
            long interval = timestampNanos - previousFrameNanos;
            if (interval > 0) {
                if (size < intervals.length) {
                    intervals[size++] = interval;
                    total += interval;
                    max = Math.max(max, interval);
                } else {
                    dropped++;
                }
            }
        }
        previousFrameNanos = timestampNanos;
    }

    @Override public long sampleCount() { return size; }
    @Override public long droppedSampleCount() { return dropped; }
    @Override public long meanIntervalNanos() { return size == 0 ? 0 : total / size; }
    @Override public long maxIntervalNanos() { return max; }

    @Override
    public long percentileIntervalNanos(double percentile) {
        if (percentile < 0 || percentile > 100 || Double.isNaN(percentile)) {
            throw new IllegalArgumentException("percentile must be between 0 and 100");
        }
        if (size == 0) return 0;
        long[] sorted = Arrays.copyOf(intervals, size);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * size) - 1;
        return sorted[Math.max(0, index)];
    }
}