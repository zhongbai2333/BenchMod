package com.zhongbai233.bench.api.neoforge.server;

import com.zhongbai233.bench.api.BenchMetricDescriptor;

public interface BenchMetricRecorder {
    void record(BenchMetricDescriptor descriptor, long value);

    void record(BenchMetricDescriptor descriptor, double value);
}