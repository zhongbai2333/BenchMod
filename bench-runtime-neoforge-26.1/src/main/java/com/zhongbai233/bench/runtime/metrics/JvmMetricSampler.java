package com.zhongbai233.bench.runtime.metrics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/** Low-frequency JVM gauges for the Server MVP. */
public final class JvmMetricSampler {
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();

    public Snapshot snapshot() {
        long gcCount = collectors.stream().mapToLong(collector -> collector.getCollectionCount())
                .filter(value -> value >= 0).sum();
        long gcTimeMillis = collectors.stream().mapToLong(collector -> collector.getCollectionTime())
                .filter(value -> value >= 0).sum();
        return new Snapshot(
                memory.getHeapMemoryUsage().getUsed(),
                memory.getNonHeapMemoryUsage().getUsed(),
                gcCount,
                gcTimeMillis,
                threads.getThreadCount());
    }

    public record Snapshot(long heapUsedBytes, long nonHeapUsedBytes, long gcCount,
                           long gcTimeMillis, int threadCount) {}
}