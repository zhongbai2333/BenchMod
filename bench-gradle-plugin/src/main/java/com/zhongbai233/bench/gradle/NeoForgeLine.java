package com.zhongbai233.bench.gradle;

import java.util.Set;

/** Selects only published adapters, never an older Minecraft development line. */
final class NeoForgeLine {
    private static final Set<String> SUPPORTED = Set.of("26.1", "26.3");

    private NeoForgeLine() {}

    static String fromVersion(String version) {
        return requireSupported(developmentLine(version));
    }

    static String developmentLine(String version) {
        String[] parts = version.split("\\.", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid NeoForge version: " + version);
        }
        return parts[0] + "." + parts[1];
    }

    static String requireSupported(String line) {
        if (!SUPPORTED.contains(line)) {
            throw new IllegalArgumentException("No ModBench adapter for NeoForge " + line
                    + "; supported lines: 26.1, 26.3. Disable automaticDependencies to supply your own adapters.");
        }
        return line;
    }

    static void requireMatching(String line, String version) {
        if (!requireSupported(line).equals(fromVersion(version))) {
            throw new IllegalArgumentException("modBench.neoForgeLine " + line
                    + " does not match NeoForge " + version);
        }
    }
}
