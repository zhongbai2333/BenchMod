package com.zhongbai233.bench.runtime.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes raw metric samples as JSON Lines, one object per metric and phase.
 *
 * <p>The summary report only carries aggregated statistics; this file preserves every recorded
 * value so offline tooling can recompute distributions or compare two runs sample by sample.
 */
public final class SampleLogWriter {
    private SampleLogWriter() {}

    public static void write(Path target, List<MetricSamples> samples) throws IOException {
        Files.createDirectories(target.getParent());
        StringBuilder out = new StringBuilder(1 << 16);
        for (MetricSamples metric : samples) {
            out.append("{\"metric\": \"").append(BenchReportWriter.escape(metric.name()))
                    .append("\", \"unit\": \"").append(BenchReportWriter.escape(metric.unit()))
                    .append("\", \"phase\": \"").append(BenchReportWriter.escape(metric.phase()))
                    .append("\", \"dropped\": ").append(metric.dropped())
                    .append(", \"values\": [");
            double[] values = metric.values();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) out.append(',');
                out.append(BenchReportWriter.formatNumber(values[i]));
            }
            out.append("]}\n");
        }
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
    }

    /** Raw samples of one metric in one phase. */
    public record MetricSamples(String name, String unit, String phase, long dropped, double[] values) {}
}
