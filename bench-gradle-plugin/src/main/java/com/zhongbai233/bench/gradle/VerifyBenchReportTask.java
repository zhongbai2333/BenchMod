package com.zhongbai233.bench.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Verification produces no outputs worth caching")
public abstract class VerifyBenchReportTask extends DefaultTask {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REPORT_SCHEMA = "/schema/mod-bench-report-1.0.0.schema.json";

    public VerifyBenchReportTask() {
        getExpectedStatus().convention("PASSED");
        getExpectedRunType().convention("");
        getExpectedScenarioId().convention("");
        getExpectedArtifactPaths().convention(List.of());
        getExpectedDiagnostics().convention(List.of());
        getExpectedMetricNames().convention(List.of());
        getExpectedLoadedModIds().convention(List.of());
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getReportFile();

    @Input
    public abstract Property<String> getExpectedStatus();

    @Input
    public abstract Property<String> getExpectedRunType();

    @Input
    public abstract Property<String> getExpectedScenarioId();

    /** Report-relative artifact paths that must be listed and exist as non-empty files. */
    @Input
    public abstract ListProperty<String> getExpectedArtifactPaths();

    /** Diagnostic messages that must appear verbatim in the report. */
    @Input
    public abstract ListProperty<String> getExpectedDiagnostics();

    /** Metric names that must appear as summarized scenario metrics. */
    @Input
    public abstract ListProperty<String> getExpectedMetricNames();

    /** Mod ids that must appear in the environment's loadedMods array. */
    @Input
    public abstract ListProperty<String> getExpectedLoadedModIds();

    @TaskAction
    public void verify() throws Exception {
        Path report = getReportFile().get().getAsFile().toPath();
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException("Missing ModBench report: " + report);
        }
        JsonNode root = JSON.readTree(report.toFile());
        validateSchema(root);

        String actualStatus = root.path("summary").path("status").asText();
        if (!actualStatus.equals(getExpectedStatus().get())) {
            throw new IllegalStateException("Expected benchmark status " + getExpectedStatus().get()
                    + " but found " + actualStatus);
        }
        String runType = getExpectedRunType().get();
        if (!runType.isBlank() && !runType.equals(root.path("run").path("runType").asText())) {
            throw new IllegalStateException("Benchmark report did not contain expected run type: " + runType);
        }
        String scenarioId = getExpectedScenarioId().get();
        if (!scenarioId.isBlank() && !containsText(root.path("scenarios"), "id", scenarioId)) {
            throw new IllegalStateException("Benchmark report did not contain expected scenario: " + scenarioId);
        }
        for (String artifactPath : getExpectedArtifactPaths().get()) {
            verifyArtifact(report, root, artifactPath);
        }
        for (String diagnostic : getExpectedDiagnostics().get()) {
            if (!containsText(root.path("diagnostics"), "message", diagnostic)) {
                throw new IllegalStateException("Benchmark report did not contain expected diagnostic: " + diagnostic);
            }
        }
        for (String metricName : getExpectedMetricNames().get()) {
            boolean found = StreamSupport.stream(root.path("scenarios").spliterator(), false)
                    .anyMatch(scenario -> containsText(scenario.path("metrics"), "name", metricName));
            if (!found) {
                throw new IllegalStateException("Benchmark report did not contain expected metric: " + metricName);
            }
        }
        for (String modId : getExpectedLoadedModIds().get()) {
            if (!containsText(root.path("environment").path("loadedMods"), "id", modId)) {
                throw new IllegalStateException("Benchmark report did not list expected loaded mod: " + modId);
            }
        }
    }

    private static void validateSchema(JsonNode report) throws Exception {
        try (InputStream stream = VerifyBenchReportTask.class.getResourceAsStream(REPORT_SCHEMA)) {
            if (stream == null) throw new IllegalStateException("Missing bundled report schema: " + REPORT_SCHEMA);
            JsonNode schemaNode = JSON.readTree(stream);
            var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(report);
            if (!errors.isEmpty()) {
                throw new IllegalStateException("Benchmark report failed schema validation: " + errors);
            }
        }
    }

    private static boolean containsText(JsonNode array, String field, String expected) {
        return array.isArray() && StreamSupport.stream(array.spliterator(), false)
                .anyMatch(item -> expected.equals(item.path(field).asText()));
    }

    private static void verifyArtifact(Path report, JsonNode root, String artifactPath) throws Exception {
        if (artifactPath.isBlank()) return;
        String normalized = artifactPath.replace('\\', '/');
        if (!containsText(root.path("artifacts"), "path", normalized)) {
            throw new IllegalStateException("Benchmark report did not contain expected artifact: " + normalized);
        }
        Path artifact = report.getParent().resolve(artifactPath).normalize();
        if (!artifact.startsWith(report.getParent()) || !Files.isRegularFile(artifact) || Files.size(artifact) == 0) {
            throw new IllegalStateException("Benchmark artifact is missing or empty: " + artifact);
        }
    }
}
