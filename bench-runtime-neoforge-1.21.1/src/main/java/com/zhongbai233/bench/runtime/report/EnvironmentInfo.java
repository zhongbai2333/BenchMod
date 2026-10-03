package com.zhongbai233.bench.runtime.report;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Everything a report needs to decide whether two runs are comparable.
 *
 * <p>Game-side values (Minecraft/NeoForge versions, loaded mods) are passed in by the caller so
 * this class stays loadable in plain JVM unit tests.
 */
public record EnvironmentInfo(
        String osName,
        String osVersion,
        String osArch,
        int logicalProcessors,
        long maxHeapBytes,
        String javaVersion,
        String javaVendor,
        String vmName,
        String vmVersion,
        List<String> jvmArgs,
        String minecraftVersion,
        String neoForgeVersion,
        List<ModEntry> loadedMods,
        String gitCommit,
        String gitDirty,
        String ciRunId) {

    /** JVM arguments whose value must never end up in a shareable report. */
    private static final Pattern SENSITIVE_ARG =
            Pattern.compile("(?i)(token|secret|password|credential|api[-_.]?key)");

    public EnvironmentInfo {
        jvmArgs = List.copyOf(jvmArgs);
        loadedMods = List.copyOf(loadedMods);
    }

    /** Captures the JVM and OS side; game versions and mods are supplied by the runtime adapter. */
    public static EnvironmentInfo capture(String minecraftVersion, String neoForgeVersion, List<ModEntry> loadedMods) {
        Runtime runtime = Runtime.getRuntime();
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .map(EnvironmentInfo::redactSensitive)
                .toList();
        return new EnvironmentInfo(
                System.getProperty("os.name", ""),
                System.getProperty("os.version", ""),
                System.getProperty("os.arch", ""),
                runtime.availableProcessors(),
                runtime.maxMemory(),
                System.getProperty("java.version", ""),
                System.getProperty("java.vendor", ""),
                System.getProperty("java.vm.name", ""),
                System.getProperty("java.vm.version", ""),
                jvmArgs,
                Objects.requireNonNullElse(minecraftVersion, ""),
                Objects.requireNonNullElse(neoForgeVersion, ""),
                loadedMods,
                firstNonBlank(System.getProperty("modBench.git.commit", ""), envOrEmpty("GITHUB_SHA")),
                System.getProperty("modBench.git.dirty", ""),
                envOrEmpty("GITHUB_RUN_ID"));
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred.isBlank() ? fallback : preferred;
    }

    static String redactSensitive(String argument) {
        if (!SENSITIVE_ARG.matcher(argument).find()) return argument;
        int separator = argument.indexOf('=');
        return separator < 0 ? "<redacted>" : argument.substring(0, separator + 1) + "<redacted>";
    }

    private static String envOrEmpty(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    /** One loaded mod as it appears in the report's {@code loadedMods} array. */
    public record ModEntry(String id, String version) {
        public ModEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(version, "version");
        }
    }
}
