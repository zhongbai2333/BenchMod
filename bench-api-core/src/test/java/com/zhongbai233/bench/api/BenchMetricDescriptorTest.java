package com.zhongbai233.bench.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BenchMetricDescriptorTest {
    @Test
    void rejectsNonPortableMetricNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new BenchMetricDescriptor("Tick Duration", "ns", MetricDirection.LOWER_IS_BETTER));
    }
}