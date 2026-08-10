package com.zhongbai233.bench.runtime.metrics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.BenchPhase;
import com.zhongbai233.bench.api.MetricDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioMetricCollectorTest {
    private static final BenchMetricDescriptor TICK =
            new BenchMetricDescriptor("server.tick.duration", "ns", MetricDirection.LOWER_IS_BETTER);
    private static final BenchMetricDescriptor CUSTOM =
            new BenchMetricDescriptor("example.workload", "count", MetricDirection.NEUTRAL);

    @Test
    void groupsSamplesByMetricAndPhase() {
        ScenarioMetricCollector collector = new ScenarioMetricCollector(16);
        collector.record(TICK, BenchPhase.WARMUP, 10.0);
        collector.record(TICK, BenchPhase.MEASURE, 20.0);
        collector.record(TICK, BenchPhase.MEASURE, 30.0);
        collector.record(CUSTOM, BenchPhase.MEASURE, 5.0);

        List<ScenarioMetricCollector.Entry> entries = collector.entries();
        assertEquals(3, entries.size());
        ScenarioMetricCollector.Entry measureTick = entries.stream()
                .filter(entry -> entry.phase() == BenchPhase.MEASURE && entry.descriptor() == TICK)
                .findFirst().orElseThrow();
        assertEquals(2, measureTick.summary().count());
        assertEquals(25.0, measureTick.summary().mean(), 1.0E-9);
        assertArrayEquals(new double[] {20.0, 30.0}, collector.samples("server.tick.duration", BenchPhase.MEASURE));
        assertEquals(0, collector.samples("server.tick.duration", BenchPhase.VERIFY).length);
    }

    @Test
    void resetClearsEverything() {
        ScenarioMetricCollector collector = new ScenarioMetricCollector(16);
        collector.record(TICK, BenchPhase.MEASURE, 1.0);
        collector.reset();

        assertTrue(collector.entries().isEmpty());
        assertEquals(0, collector.samples("server.tick.duration", BenchPhase.MEASURE).length);
    }
}
