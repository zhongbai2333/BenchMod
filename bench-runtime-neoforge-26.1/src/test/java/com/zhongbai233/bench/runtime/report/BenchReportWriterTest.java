package com.zhongbai233.bench.runtime.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchStatus;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class BenchReportWriterTest {
    @Test
    void recordsInjectedComponentVersions(@TempDir Path directory) throws Exception {
        String[] components = {"plugin", "apiCore", "apiNeoforge", "runtime", "schema"};
        try {
            for (String component : components) {
                System.setProperty("modBench.version." + component, component + "-version");
            }
            BenchReportWriter writer = new BenchReportWriter(directory, "target", 7L);
            writer.writeFinal(BenchStatus.PASSED);

            String json = Files.readString(directory.resolve("summary.json"));
            for (String component : components) {
                assertTrue(json.contains("\"" + component + "\": \"" + component + "-version\""));
            }
        } finally {
            for (String component : components) System.clearProperty("modBench.version." + component);
        }
    }

    @Test
    void writesScreenshotArtifact(@TempDir Path directory) throws Exception {
        BenchReportWriter writer = new BenchReportWriter(directory, "target", 7L, "client", "INTEGRATED_CLIENT");
        writer.addArtifact("screenshot", "artifacts/screenshots/view.png", "image/png");

        writer.writeFinal(BenchStatus.PASSED);

        String report = Files.readString(directory.resolve("summary.json"));
        assertTrue(report.contains("\"type\": \"screenshot\""));
        assertTrue(report.contains("\"path\": \"artifacts/screenshots/view.png\""));
        assertTrue(report.contains("\"contentType\": \"image/png\""));
    }

    @Test
    void writesPhasesMetricsAndEnvironment(@TempDir Path directory) throws Exception {
        BenchReportWriter writer = new BenchReportWriter(directory, "target", 7L);
        writer.setEnvironment(new EnvironmentInfo(
                "TestOS", "1.0", "amd64", 8, 1024L,
                "25", "Vendor", "TestVM", "25.0.1", List.of("-Xmx2G"),
                "26.1.2", "26.1.2.76",
                List.of(new EnvironmentInfo.ModEntry("minecraft", "26.1.2")),
                "abc123", "true", "42"));
        writer.addRunParameter("phaseTimeoutTicks", "600");
        writer.addRunParameter("label", "smoke");
        writer.addScenario("example.scenario", "example", BenchStatus.PASSED, null,
                List.of(new BenchReportWriter.PhaseEntry("MEASURE", 10, 50, 2_000_000L, "completed")),
                List.of(new BenchReportWriter.MetricEntry(
                        "server.tick.duration", "ns", "LOWER_IS_BETTER", "MEASURE",
                        40, 0, 1.0E6, 4.0E6, 2.5E6, 2.0E6, 3.0E6, 3.5E6, 4.0E6, 0.5,
                        Map.of("over_50ms_frames", 0L))));

        writer.writeFinal(BenchStatus.PASSED);
        String report = Files.readString(directory.resolve("summary.json"));

        assertTrue(report.contains("\"os\": {\"name\": \"TestOS\", \"version\": \"1.0\", \"arch\": \"amd64\"}"));
        assertTrue(report.contains("\"cpu\": {\"logicalProcessors\": 8}"));
        assertTrue(report.contains("\"jvmArgs\": [\"-Xmx2G\"]"));
        assertTrue(report.contains("\"minecraft\": \"26.1.2\""));
        assertTrue(report.contains("\"neoforge\": \"26.1.2.76\""));
        assertTrue(report.contains("{\"id\": \"minecraft\", \"version\": \"26.1.2\"}"));
        assertTrue(report.contains("\"git\": {\"commit\": \"abc123\", \"dirty\": true}"));
        assertTrue(report.contains("\"ci\": {\"runId\": \"42\"}"));
        assertTrue(report.contains("\"parameters\": {\"phaseTimeoutTicks\": 600, \"label\": \"smoke\"}"));
        assertTrue(report.contains(
                "{\"phase\": \"MEASURE\", \"outcome\": \"completed\", \"startTick\": 10, \"endTick\": 50, "
                        + "\"durationTicks\": 40, \"wallNanos\": 2000000}"));
        assertTrue(report.contains("\"name\": \"server.tick.duration\""));
        assertTrue(report.contains("\"mean\": 2500000"));
        assertTrue(report.contains("\"stdDev\": 0.5"));
        assertTrue(report.contains("\"extra\": {\"over_50ms_frames\": 0}"));
    }

    @Test
    void writesAMarkdownDerivedView(@TempDir Path directory) throws Exception {
        BenchReportWriter writer = new BenchReportWriter(directory, "target", 7L);
        writer.addScenario("example.scenario", "example", BenchStatus.PASSED, null,
                List.of(new BenchReportWriter.PhaseEntry("MEASURE", 10, 50, 2_000_000L, "completed")),
                List.of(new BenchReportWriter.MetricEntry(
                        "server.tick.duration", "ns", "LOWER_IS_BETTER", "MEASURE",
                        40, 0, 1.0E6, 4.0E6, 2.5E6, 2.0E6, 3.0E6, 3.5E6, 4.0E6, 0.5, Map.of())));
        writer.addArtifact("samples", "artifacts/samples/example.jsonl", "application/x-ndjson", "", 128);

        writer.writeFinal(BenchStatus.PASSED);
        String markdown = Files.readString(directory.resolve("report.md"));

        assertTrue(markdown.contains("# ModBench Report"));
        assertTrue(markdown.contains("- Status: **PASSED**"));
        assertTrue(markdown.contains("| `example.scenario` | example | PASSED |"));
        assertTrue(markdown.contains("| MEASURE | completed | 40 | 2.000 |"));
        assertTrue(markdown.contains("| `server.tick.duration` | MEASURE | 40 | 2.500 ms |"));
        assertTrue(markdown.contains("| samples | `artifacts/samples/example.jsonl` | 128 |"));
    }

    @Test
    void formatsIntegralValuesWithoutDecimalPoint() {
        assertEquals("2500000", BenchReportWriter.formatNumber(2.5E6));
        assertEquals("0.5", BenchReportWriter.formatNumber(0.5));
        assertEquals("0", BenchReportWriter.formatNumber(Double.NaN));
    }

    @Test
    void redactsSensitiveJvmArguments() {
        assertEquals("-Dapi.token=<redacted>", EnvironmentInfo.redactSensitive("-Dapi.token=abc"));
        assertEquals("-Dmy.password=<redacted>", EnvironmentInfo.redactSensitive("-Dmy.password=hunter2"));
        assertEquals("-Xmx2G", EnvironmentInfo.redactSensitive("-Xmx2G"));
        assertEquals("-DmodBench.seed=7", EnvironmentInfo.redactSensitive("-DmodBench.seed=7"));
    }
}