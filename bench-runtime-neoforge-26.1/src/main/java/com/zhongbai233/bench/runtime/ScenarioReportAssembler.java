package com.zhongbai233.bench.runtime;

import com.zhongbai233.bench.runtime.metrics.FrameBudgetExtras;
import com.zhongbai233.bench.runtime.metrics.MetricSummary;
import com.zhongbai233.bench.runtime.metrics.ScenarioMetricCollector;
import com.zhongbai233.bench.runtime.report.BenchReportWriter;
import com.zhongbai233.bench.runtime.report.SampleLogWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts runner timelines and metric accumulators into report entries. */
public final class ScenarioReportAssembler {
    private ScenarioReportAssembler() {}

    public static List<BenchReportWriter.PhaseEntry> phases(List<PhaseTimeline.PhaseRecord> records) {
        List<BenchReportWriter.PhaseEntry> entries = new ArrayList<>(records.size());
        for (PhaseTimeline.PhaseRecord record : records) {
            entries.add(new BenchReportWriter.PhaseEntry(
                    record.phase().name(), record.startTick(), record.endTick(), record.wallNanos(),
                    record.outcome()));
        }
        return entries;
    }

    /** Extracts every raw sample series for the JSON Lines artifact. */
    public static List<SampleLogWriter.MetricSamples> samples(ScenarioMetricCollector collector) {
        List<SampleLogWriter.MetricSamples> samples = new ArrayList<>();
        for (ScenarioMetricCollector.Entry entry : collector.entries()) {
            samples.add(new SampleLogWriter.MetricSamples(
                    entry.descriptor().name(),
                    entry.descriptor().unit(),
                    entry.phase().name(),
                    entry.summary().dropped(),
                    collector.samples(entry.descriptor().name(), entry.phase())));
        }
        return samples;
    }

    /**
     * Summarizes every accumulated metric. {@code frameMetricName} selects the metric that gets
     * frame-budget extras (percentile lows and over-budget counts); pass {@code null} for none.
     */
    public static List<BenchReportWriter.MetricEntry> metrics(ScenarioMetricCollector collector,
                                                              String frameMetricName) {
        List<BenchReportWriter.MetricEntry> entries = new ArrayList<>();
        for (ScenarioMetricCollector.Entry entry : collector.entries()) {
            MetricSummary summary = entry.summary();
            Map<String, Long> extra = entry.descriptor().name().equals(frameMetricName)
                    ? FrameBudgetExtras.compute(collector.samples(entry.descriptor().name(), entry.phase()))
                    : Map.of();
            entries.add(new BenchReportWriter.MetricEntry(
                    entry.descriptor().name(),
                    entry.descriptor().unit(),
                    entry.descriptor().direction().name(),
                    entry.phase().name(),
                    summary.count(),
                    summary.dropped(),
                    summary.min(),
                    summary.max(),
                    summary.mean(),
                    summary.median(),
                    summary.p90(),
                    summary.p95(),
                    summary.p99(),
                    summary.stdDev(),
                    extra));
        }
        return entries;
    }
}
