package com.zhongbai233.bench.api.neoforge.client;

/** Read-only frame interval statistics accumulated by the Runtime. */
public interface BenchFrameMetrics {
    long sampleCount();

    long droppedSampleCount();

    long meanIntervalNanos();

    long percentileIntervalNanos(double percentile);

    long maxIntervalNanos();
}