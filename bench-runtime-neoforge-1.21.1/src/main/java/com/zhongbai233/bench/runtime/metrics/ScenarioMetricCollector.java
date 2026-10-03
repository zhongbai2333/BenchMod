package com.zhongbai233.bench.runtime.metrics;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.BenchPhase;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates every metric of one scenario, keyed by descriptor and lifecycle phase.
 *
 * <p>Lookup is an {@link EnumMap} step plus a string-keyed map hit, so repeated records for the
 * same metric do not allocate.
 */
public final class ScenarioMetricCollector {
    /** Bounds each accumulator; ~0.5 MiB of samples per metric and phase. */
    public static final int DEFAULT_CAPACITY = 65_536;

    private final int capacity;
    private final EnumMap<BenchPhase, Map<String, Slot>> byPhase = new EnumMap<>(BenchPhase.class);

    public ScenarioMetricCollector() {
        this(DEFAULT_CAPACITY);
    }

    public ScenarioMetricCollector(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public void record(BenchMetricDescriptor descriptor, BenchPhase phase, double value) {
        byPhase.computeIfAbsent(phase, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(descriptor.name(), ignored -> new Slot(descriptor, new MetricAccumulator(capacity)))
                .accumulator()
                .record(value);
    }

    /** Raw samples for one metric in one phase, or an empty array when nothing was recorded. */
    public double[] samples(String metricName, BenchPhase phase) {
        Map<String, Slot> slots = byPhase.get(phase);
        Slot slot = slots == null ? null : slots.get(metricName);
        return slot == null ? new double[0] : slot.accumulator().copyValues();
    }

    /** Every recorded metric in phase order, ready for report serialization. */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        byPhase.forEach((phase, slots) -> slots.forEach((name, slot) ->
                entries.add(new Entry(slot.descriptor(), phase, slot.accumulator().summarize()))));
        return entries;
    }

    /** Clears every accumulator so the next scenario starts fresh. */
    public void reset() {
        byPhase.clear();
    }

    public record Entry(BenchMetricDescriptor descriptor, BenchPhase phase, MetricSummary summary) {}

    private record Slot(BenchMetricDescriptor descriptor, MetricAccumulator accumulator) {}
}
