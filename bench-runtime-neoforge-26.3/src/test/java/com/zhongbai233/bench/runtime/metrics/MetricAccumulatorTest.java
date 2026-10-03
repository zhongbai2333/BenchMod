package com.zhongbai233.bench.runtime.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MetricAccumulatorTest {
    @Test
    void summarizesOrderStatisticsAndMoments() {
        MetricAccumulator accumulator = new MetricAccumulator(100);
        for (int value = 1; value <= 100; value++) accumulator.record(value);

        MetricSummary summary = accumulator.summarize();
        assertEquals(100, summary.count());
        assertEquals(0, summary.dropped());
        assertEquals(1.0, summary.min());
        assertEquals(100.0, summary.max());
        assertEquals(50.5, summary.mean(), 1.0E-9);
        assertEquals(50.0, summary.median());
        assertEquals(90.0, summary.p90());
        assertEquals(95.0, summary.p95());
        assertEquals(99.0, summary.p99());
        assertEquals(28.866, summary.stdDev(), 0.001);
    }

    @Test
    void overflowDropsInsteadOfResizing() {
        MetricAccumulator accumulator = new MetricAccumulator(3);
        for (int value = 0; value < 10; value++) accumulator.record(value);

        assertEquals(3, accumulator.sampleCount());
        assertEquals(7, accumulator.droppedCount());
        assertEquals(3, accumulator.summarize().count());
        assertEquals(7, accumulator.summarize().dropped());
        assertEquals(2.0, accumulator.summarize().max());
    }

    @Test
    void emptyAccumulatorSummarizesToZeros() {
        MetricSummary summary = new MetricAccumulator(4).summarize();
        assertEquals(0, summary.count());
        assertEquals(0.0, summary.mean());
        assertEquals(0.0, summary.p99());
    }

    @Test
    void singleSampleIsEveryStatistic() {
        MetricAccumulator accumulator = new MetricAccumulator(4);
        accumulator.record(42.0);

        MetricSummary summary = accumulator.summarize();
        assertEquals(42.0, summary.min());
        assertEquals(42.0, summary.median());
        assertEquals(42.0, summary.p99());
        assertEquals(0.0, summary.stdDev());
    }
}
