package com.zhongbai233.bench.runtime.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.BenchStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioArtifactWriterTest {
    @TempDir Path directory;

    @Test
    void writesUnderArtifactsCustomAndRegistersWithHashAndSize() throws Exception {
        BenchReportWriter report = new BenchReportWriter(directory, "target", 7L);
        ScenarioArtifactWriter writer = new ScenarioArtifactWriter(directory, report);

        Path written = writer.write("trace.csv", "text/csv", "tick,value\n1,2\n");
        report.writeFinal(BenchStatus.PASSED);

        assertEquals(directory.resolve("artifacts").resolve("custom").resolve("trace.csv"), written);
        String summary = Files.readString(directory.resolve("summary.json"));
        assertTrue(summary.contains("\"type\": \"custom\", \"path\": \"artifacts/custom/trace.csv\""));
        assertTrue(summary.contains("\"contentType\": \"text/csv\""));
        assertTrue(summary.contains("\"bytes\": 15"));
        assertTrue(summary.matches("(?s).*\"sha256\": \"[0-9a-f]{64}\".*"));
    }

    @Test
    void registersExistingFilesInsideTheResultDirectoryOnly() throws Exception {
        BenchReportWriter report = new BenchReportWriter(directory, "target", 7L);
        ScenarioArtifactWriter writer = new ScenarioArtifactWriter(directory, report);
        Path existing = directory.resolve("rope-stack-trace.csv");
        Files.writeString(existing, "a,b\n");

        writer.register(existing, "text/csv");
        report.writeFinal(BenchStatus.PASSED);

        assertTrue(Files.readString(directory.resolve("summary.json"))
                .contains("\"path\": \"rope-stack-trace.csv\""));
        assertThrows(IllegalArgumentException.class,
                () -> writer.register(directory.resolve("../outside.csv"), "text/csv"));
        assertThrows(IOException.class,
                () -> writer.register(directory.resolve("missing.csv"), "text/csv"));
    }

    @Test
    void rejectsUnsafeFilenames() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioArtifactWriter.safeFilename("../escape.csv"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioArtifactWriter.safeFilename("nested/file.csv"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioArtifactWriter.safeFilename(" "));
        assertEquals("trace.csv", ScenarioArtifactWriter.safeFilename("trace.csv"));
    }
}
