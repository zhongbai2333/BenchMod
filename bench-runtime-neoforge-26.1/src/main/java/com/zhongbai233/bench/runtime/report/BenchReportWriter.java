package com.zhongbai233.bench.runtime.report;

import com.zhongbai233.bench.api.BenchStatus;
import com.zhongbai233.bench.api.BenchProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a schema-shaped partial/final report via atomic replacement. */
public final class BenchReportWriter {
    private static final String SCHEMA_VERSION = "1.0.0";
    private final Path directory;
    private final Instant startedAt = Instant.now();
    private final String targetMod;
    private final long seed;
    private final String runType;
    private final String side;
    private final List<ScenarioEntry> scenarios = new ArrayList<>();
    private final List<ProviderEntry> providers = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final List<ArtifactEntry> artifacts = new ArrayList<>();
    private final Map<String, String> runParameters = new LinkedHashMap<>();
    private EnvironmentInfo environment;

    public BenchReportWriter(Path directory, String targetMod, long seed) {
        this(directory, targetMod, seed, "server", "DEDICATED_SERVER");
    }

    public BenchReportWriter(Path directory, String targetMod, long seed, String runType, String side) {
        this.directory = directory;
        this.targetMod = targetMod;
        this.seed = seed;
        this.runType = runType;
        this.side = side;
    }

    public void addScenario(String id, String providerId, BenchStatus status, String failure) {
        addScenario(id, providerId, status, failure, List.of(), List.of());
    }

    public void addScenario(String id, String providerId, BenchStatus status, String failure,
                            List<PhaseEntry> phases, List<MetricEntry> metrics) {
        scenarios.add(new ScenarioEntry(id, providerId, status, failure, List.copyOf(phases), List.copyOf(metrics)));
    }

    public void addProvider(BenchProvider provider) {
        providers.add(new ProviderEntry(
                provider.id(),
                provider.getClass().getName(),
                provider.compatibility().apiMajor(),
                provider.compatibility().minimumApiMinor(),
                provider.compatibility().maximumApiMinor()));
    }

    public void addDiagnostic(String message) {
        diagnostics.add(message);
    }

    public void addArtifact(String type, String path, String contentType) {
        addArtifact(type, path, contentType, "", -1L);
    }

    /** Records an artifact with the content hash and size a consumer needs to compare two runs. */
    public void addArtifact(String type, String path, String contentType, String sha256, long bytes) {
        artifacts.add(new ArtifactEntry(type, path, contentType, sha256, bytes));
    }

    /** Attaches the full machine/game environment; without it a minimal fallback is written. */
    public void setEnvironment(EnvironmentInfo environment) {
        this.environment = environment;
    }

    /** Adds a workload parameter (timeouts, expected counts, graphics baseline) to the run object. */
    public void addRunParameter(String key, String value) {
        runParameters.put(key, value);
    }

    public void writePartial() throws IOException {
        write("partial-report.json", false);
    }

    public void writeFinal(BenchStatus overallStatus) throws IOException {
        write("summary.json", true, overallStatus);
        writeMarkdown(overallStatus);
    }

    /** Best-effort derived view; the JSON stays authoritative and a Markdown failure is ignored. */
    private void writeMarkdown(BenchStatus overall) {
        try {
            Files.writeString(directory.resolve("report.md"), markdown(overall), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // summary.json was already written; the derived view is optional.
        }
    }

    private void write(String filename, boolean complete) throws IOException {
        BenchStatus inferred = scenarios.stream().allMatch(entry -> entry.status() == BenchStatus.PASSED)
                && complete ? BenchStatus.PASSED : BenchStatus.FAILED;
        write(filename, complete, inferred);
    }

    private void write(String filename, boolean complete, BenchStatus overall) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(filename);
        Path temporary = directory.resolve(filename + ".tmp");
        Files.writeString(temporary, json(complete, overall), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String json(boolean complete, BenchStatus overall) {
        StringBuilder out = new StringBuilder(8192);
        out.append("{\n  \"schema\": \"").append(SCHEMA_VERSION).append("\",");
        appendRun(out);
        appendEnvironment(out);
        out.append("\n  \"artifacts\": [");
        for (int i = 0; i < artifacts.size(); i++) {
            if (i > 0) out.append(',');
            appendArtifact(out, artifacts.get(i));
        }
        out.append("\n  ],\n  \"providers\": [");
        for (int i = 0; i < providers.size(); i++) {
            if (i > 0) out.append(',');
            appendProvider(out, providers.get(i));
        }
        out.append("\n  ],\n  \"scenarios\": [");
        for (int i = 0; i < scenarios.size(); i++) {
            if (i > 0) out.append(',');
            appendScenario(out, scenarios.get(i));
        }
        out.append("\n  ],\n  \"summary\": {\"status\": \"").append(overall)
                .append("\", \"counts\": {\"total\": ").append(scenarios.size()).append("}},");
        out.append("\n  \"diagnostics\": [");
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) out.append(',');
            out.append("{\"message\": \"").append(escape(diagnostics.get(i))).append("\"}");
        }
        return out.append("]\n}\n").toString();
    }

    private void appendRun(StringBuilder out) {
        out.append("\n  \"run\": {\"id\": \"").append(escape(runType + "-" + startedAt.toEpochMilli()))
            .append("\", \"suite\": \"default\", \"runType\": \"").append(escape(runType))
            .append("\", \"side\": \"").append(escape(side)).append("\", \"seed\": ")
                .append(seed).append(", \"startedAt\": \"").append(startedAt)
                .append("\", \"finishedAt\": \"").append(Instant.now()).append('"');
        if (!runParameters.isEmpty()) {
            out.append(", \"parameters\": {");
            boolean first = true;
            for (Map.Entry<String, String> parameter : runParameters.entrySet()) {
                if (!first) out.append(", ");
                first = false;
                out.append('"').append(escape(parameter.getKey())).append("\": ");
                appendScalar(out, parameter.getValue());
            }
            out.append('}');
        }
        out.append("},");
    }

    private void appendEnvironment(StringBuilder out) {
        if (environment == null) {
            out.append("\n  \"environment\": {\"os\": {\"name\": \"").append(escape(System.getProperty("os.name")))
                    .append("\"}, \"java\": {\"version\": \"").append(escape(System.getProperty("java.version")))
                    .append("\"}, \"versions\": {");
            appendVersions(out);
            out.append("}, \"loadedMods\": []},");
            return;
        }
        out.append("\n  \"environment\": {");
        out.append("\n    \"os\": {\"name\": \"").append(escape(environment.osName()))
                .append("\", \"version\": \"").append(escape(environment.osVersion()))
                .append("\", \"arch\": \"").append(escape(environment.osArch())).append("\"},");
        out.append("\n    \"cpu\": {\"logicalProcessors\": ").append(environment.logicalProcessors()).append("},");
        out.append("\n    \"memory\": {\"maxHeapBytes\": ").append(environment.maxHeapBytes()).append("},");
        out.append("\n    \"java\": {\"version\": \"").append(escape(environment.javaVersion()))
                .append("\", \"vendor\": \"").append(escape(environment.javaVendor()))
                .append("\", \"vmName\": \"").append(escape(environment.vmName()))
                .append("\", \"vmVersion\": \"").append(escape(environment.vmVersion()))
                .append("\", \"jvmArgs\": [");
        List<String> jvmArgs = environment.jvmArgs();
        for (int i = 0; i < jvmArgs.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(escape(jvmArgs.get(i))).append('"');
        }
        out.append("]},");
        out.append("\n    \"versions\": {");
        appendVersions(out);
        if (!environment.minecraftVersion().isBlank()) {
            out.append(", \"minecraft\": \"").append(escape(environment.minecraftVersion())).append('"');
        }
        if (!environment.neoForgeVersion().isBlank()) {
            out.append(", \"neoforge\": \"").append(escape(environment.neoForgeVersion())).append('"');
        }
        out.append("},");
        if (!environment.gitCommit().isBlank()) {
            out.append("\n    \"git\": {\"commit\": \"").append(escape(environment.gitCommit())).append('"');
            if (environment.gitDirty().equals("true") || environment.gitDirty().equals("false")) {
                out.append(", \"dirty\": ").append(environment.gitDirty());
            }
            out.append("},");
        }
        if (!environment.ciRunId().isBlank()) {
            out.append("\n    \"ci\": {\"runId\": \"").append(escape(environment.ciRunId())).append("\"},");
        }
        out.append("\n    \"loadedMods\": [");
        List<EnvironmentInfo.ModEntry> mods = environment.loadedMods();
        for (int i = 0; i < mods.size(); i++) {
            if (i > 0) out.append(',');
            out.append("\n      {\"id\": \"").append(escape(mods.get(i).id()))
                    .append("\", \"version\": \"").append(escape(mods.get(i).version())).append("\"}");
        }
        out.append("\n    ]\n  },");
    }

    private static void appendVersions(StringBuilder out) {
        out.append("\"plugin\": \"").append(escape(versionProperty("plugin", "unknown")))
                .append("\", \"apiCore\": \"").append(escape(versionProperty("apiCore", "unknown")))
                .append("\", \"apiNeoforge\": \"").append(escape(versionProperty("apiNeoforge", "unknown")))
                .append("\", \"runtime\": \"").append(escape(runtimeVersion()))
                .append("\", \"schema\": \"").append(escape(versionProperty("schema", SCHEMA_VERSION)))
                .append('"');
    }

    private static String versionProperty(String component, String fallback) {
        return System.getProperty("modBench.version." + component, fallback);
    }

    private static String runtimeVersion() {
        String manifestVersion = BenchReportWriter.class.getPackage().getImplementationVersion();
        String fallback = manifestVersion == null || manifestVersion.isBlank() ? "unknown" : manifestVersion;
        return versionProperty("runtime", fallback);
    }

    private void appendArtifact(StringBuilder out, ArtifactEntry artifact) {
        out.append("\n    {\"type\": \"").append(escape(artifact.type())).append("\", \"path\": \"")
            .append(escape(artifact.path())).append("\", \"contentType\": \"")
            .append(escape(artifact.contentType())).append('"');
        if (!artifact.sha256().isBlank()) {
            out.append(", \"sha256\": \"").append(escape(artifact.sha256())).append('"');
        }
        if (artifact.bytes() >= 0) out.append(", \"bytes\": ").append(artifact.bytes());
        out.append('}');
    }

    private void appendProvider(StringBuilder out, ProviderEntry provider) {
        out.append("\n    {\"id\": \"").append(escape(provider.id())).append("\", \"className\": \"")
            .append(escape(provider.className())).append("\", \"targetMod\": \"")
            .append(escape(targetMod)).append("\", \"apiCompatibility\": {\"major\": ")
            .append(provider.major()).append(", \"minimumMinor\": ").append(provider.minimumMinor())
            .append(", \"maximumMinor\": ").append(provider.maximumMinor()).append("}}");
    }

    private void appendScenario(StringBuilder out, ScenarioEntry entry) {
        out.append("\n    {\"id\": \"").append(escape(entry.id())).append("\", \"providerId\": \"")
                .append(escape(entry.providerId())).append("\", \"status\": \"").append(entry.status())
                .append("\", \"workloadCorrect\": ").append(entry.status() == BenchStatus.PASSED)
                .append(", \"failure\": ");
        if (entry.failure() == null) out.append("null");
        else out.append("{\"message\": \"").append(escape(entry.failure())).append("\"}");
        out.append(", \"phases\": [");
        List<PhaseEntry> phases = entry.phases();
        for (int i = 0; i < phases.size(); i++) {
            if (i > 0) out.append(',');
            PhaseEntry phase = phases.get(i);
            out.append("\n      {\"phase\": \"").append(escape(phase.phase()))
                    .append("\", \"outcome\": \"").append(escape(phase.outcome()))
                    .append("\", \"startTick\": ").append(phase.startTick())
                    .append(", \"endTick\": ").append(phase.endTick())
                    .append(", \"durationTicks\": ").append(phase.endTick() - phase.startTick())
                    .append(", \"wallNanos\": ").append(phase.wallNanos()).append('}');
        }
        if (!phases.isEmpty()) out.append("\n    ");
        out.append("], \"metrics\": [");
        List<MetricEntry> metrics = entry.metrics();
        for (int i = 0; i < metrics.size(); i++) {
            if (i > 0) out.append(',');
            appendMetric(out, metrics.get(i));
        }
        if (!metrics.isEmpty()) out.append("\n    ");
        out.append("]}");
    }

    private void appendMetric(StringBuilder out, MetricEntry metric) {
        out.append("\n      {\"name\": \"").append(escape(metric.name()))
                .append("\", \"unit\": \"").append(escape(metric.unit()))
                .append("\", \"direction\": \"").append(escape(metric.direction()))
                .append("\", \"phase\": \"").append(escape(metric.phase()))
                .append("\", \"count\": ").append(metric.count())
                .append(", \"dropped\": ").append(metric.dropped());
        appendNumberField(out, "min", metric.min());
        appendNumberField(out, "max", metric.max());
        appendNumberField(out, "mean", metric.mean());
        appendNumberField(out, "median", metric.median());
        appendNumberField(out, "p90", metric.p90());
        appendNumberField(out, "p95", metric.p95());
        appendNumberField(out, "p99", metric.p99());
        appendNumberField(out, "stdDev", metric.stdDev());
        if (!metric.extra().isEmpty()) {
            out.append(", \"extra\": {");
            boolean first = true;
            for (Map.Entry<String, Long> extra : metric.extra().entrySet()) {
                if (!first) out.append(", ");
                first = false;
                out.append('"').append(escape(extra.getKey())).append("\": ").append(extra.getValue());
            }
            out.append('}');
        }
        out.append('}');
    }

    String markdown(BenchStatus overall) {
        StringBuilder out = new StringBuilder(4096);
        out.append("# ModBench Report\n\n");
        out.append("- Status: **").append(overall).append("**\n");
        out.append("- Run: ").append(runType).append(" (").append(side).append("), suite `default`, seed ")
                .append(seed).append('\n');
        out.append("- Target mod: `").append(targetMod).append("`\n");
        out.append("- Started: ").append(startedAt).append('\n');
        if (environment != null) {
            out.append("- Minecraft ").append(environment.minecraftVersion())
                    .append(" / NeoForge ").append(environment.neoForgeVersion()).append('\n');
            out.append("- ").append(environment.osName()).append(' ').append(environment.osVersion())
                    .append(", Java ").append(environment.javaVersion())
                    .append(", ").append(environment.logicalProcessors()).append(" logical processors\n");
        }
        out.append("\n## Scenarios\n\n| Scenario | Provider | Status | Failure |\n|---|---|---|---|\n");
        for (ScenarioEntry entry : scenarios) {
            out.append("| `").append(entry.id()).append("` | ").append(entry.providerId())
                    .append(" | ").append(entry.status()).append(" | ")
                    .append(entry.failure() == null ? "" : entry.failure().replace("|", "\\|"))
                    .append(" |\n");
        }
        for (ScenarioEntry entry : scenarios) {
            if (entry.phases().isEmpty() && entry.metrics().isEmpty()) continue;
            out.append("\n### `").append(entry.id()).append("`\n");
            if (!entry.phases().isEmpty()) {
                out.append("\n| Phase | Outcome | Ticks | Wall ms |\n|---|---|---:|---:|\n");
                for (PhaseEntry phase : entry.phases()) {
                    out.append("| ").append(phase.phase()).append(" | ").append(phase.outcome())
                            .append(" | ").append(phase.endTick() - phase.startTick())
                            .append(" | ").append(millis(phase.wallNanos())).append(" |\n");
                }
            }
            if (!entry.metrics().isEmpty()) {
                out.append("\n| Metric | Phase | Count | Mean | Median | P95 | P99 | Max |\n")
                        .append("|---|---|---:|---:|---:|---:|---:|---:|\n");
                for (MetricEntry metric : entry.metrics()) {
                    out.append("| `").append(metric.name()).append("` | ").append(metric.phase())
                            .append(" | ").append(metric.count());
                    for (double value : new double[] {
                            metric.mean(), metric.median(), metric.p95(), metric.p99(), metric.max()}) {
                        out.append(" | ").append(metricValue(metric.unit(), value));
                    }
                    out.append(" |\n");
                }
            }
        }
        if (!artifacts.isEmpty()) {
            out.append("\n## Artifacts\n\n| Type | Path | Bytes |\n|---|---|---:|\n");
            for (ArtifactEntry artifact : artifacts) {
                out.append("| ").append(artifact.type()).append(" | `").append(artifact.path())
                        .append("` | ").append(artifact.bytes() >= 0 ? artifact.bytes() : "").append(" |\n");
            }
        }
        out.append("\nDiagnostics: ").append(diagnostics.size())
                .append(" entries in `summary.json`.\n");
        return out.toString();
    }

    /** Nanosecond metrics read best in milliseconds; other units keep their raw value. */
    private static String metricValue(String unit, double value) {
        return unit.equals("ns") ? millis(Math.round(value)) + " ms" : formatNumber(value) + " " + unit;
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1.0E6);
    }

    private static void appendNumberField(StringBuilder out, String name, double value) {
        out.append(", \"").append(name).append("\": ").append(formatNumber(value));
    }

    /** Prints integral values without a decimal point so nanosecond stats stay readable. */
    static String formatNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        if (value == Math.rint(value) && Math.abs(value) < 9.007199254740992E15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** Emits bare numbers and booleans unquoted so run parameters stay typed. */
    private static void appendScalar(StringBuilder out, String value) {
        if (value.matches("-?\\d+(\\.\\d+)?") || value.equals("true") || value.equals("false")) {
            out.append(value);
        } else {
            out.append('"').append(escape(value)).append('"');
        }
    }

    static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /** One executed lifecycle phase of a scenario. */
    public record PhaseEntry(String phase, long startTick, long endTick, long wallNanos, String outcome) {}

    /** One summarized metric of a scenario in a specific phase. */
    public record MetricEntry(
            String name,
            String unit,
            String direction,
            String phase,
            long count,
            long dropped,
            double min,
            double max,
            double mean,
            double median,
            double p90,
            double p95,
            double p99,
            double stdDev,
            Map<String, Long> extra) {
        public MetricEntry {
            extra = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(extra));
        }
    }

    private record ScenarioEntry(String id, String providerId, BenchStatus status, String failure,
                                 List<PhaseEntry> phases, List<MetricEntry> metrics) {}
    private record ProviderEntry(String id, String className, int major, int minimumMinor, int maximumMinor) {}
    private record ArtifactEntry(String type, String path, String contentType, String sha256, long bytes) {}
}
