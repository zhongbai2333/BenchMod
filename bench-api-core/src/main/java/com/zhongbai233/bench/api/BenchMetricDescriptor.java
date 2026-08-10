package com.zhongbai233.bench.api;

import java.util.Objects;

/** Stable metadata for a metric emitted by Runtime or a Provider. */
public record BenchMetricDescriptor(String name, String unit, MetricDirection direction) {
    public BenchMetricDescriptor {
        if (name == null || !name.matches("[a-z][a-z0-9._-]*")) {
            throw new IllegalArgumentException("Metric name must be lowercase and dot-separated");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Metric unit must be non-empty");
        }
        Objects.requireNonNull(direction, "direction");
    }
}