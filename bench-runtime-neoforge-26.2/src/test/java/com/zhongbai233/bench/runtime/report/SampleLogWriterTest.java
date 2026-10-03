package com.zhongbai233.bench.runtime.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SampleLogWriterTest {
    @Test
    void writesOneJsonObjectPerMetricAndPhase(@TempDir Path directory) throws Exception {
        Path target = directory.resolve("artifacts/samples/scenario.jsonl");
        SampleLogWriter.write(target, List.of(
                new SampleLogWriter.MetricSamples("server.tick.duration", "ns", "MEASURE", 0,
                        new double[] {1_000_000.0, 2_000_000.0}),
                new SampleLogWriter.MetricSamples("example.workload", "count", "MEASURE", 3,
                        new double[] {5.5})));

        List<String> lines = Files.readAllLines(target);
        assertEquals(2, lines.size());
        assertEquals("{\"metric\": \"server.tick.duration\", \"unit\": \"ns\", \"phase\": \"MEASURE\", "
                + "\"dropped\": 0, \"values\": [1000000,2000000]}", lines.get(0));
        assertTrue(lines.get(1).contains("\"dropped\": 3"));
        assertTrue(lines.get(1).contains("\"values\": [5.5]"));
    }
}
