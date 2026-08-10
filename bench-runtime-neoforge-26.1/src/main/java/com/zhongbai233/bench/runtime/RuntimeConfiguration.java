package com.zhongbai233.bench.runtime;

import java.nio.file.Path;

public record RuntimeConfiguration(
        Path resultDirectory,
        int expectedProviderCount,
        long seed,
        long phaseTimeoutTicks,
        String targetMod,
        String clientWorldId,
        boolean clientAutoWorld,
        int clientWindowWidth,
        int clientWindowHeight,
        boolean clientVsync,
        int clientFpsLimit,
        int clientRenderDistance,
        int clientSimulationDistance,
        boolean clientRequireWindowFocus,
        double clientStableFrameRatio,
        int clientCaptureGateFrameBudget,
        String clientWorldPreset,
        String clientDimension,
        String serverLevelType,
        String serverGeneratorSettings,
        boolean jfrEnabled,
        String scenarioFilter,
        String participantMode,
        String pairedSessionId) {
    /** Overworld generation presets a client benchmark world can use. */
    public static final java.util.Set<String> WORLD_PRESETS = java.util.Set.of("normal", "flat", "void");
    /** Dimensions the benchmark player can be placed in before scenarios start. */
    public static final java.util.Set<String> DIMENSIONS = java.util.Set.of("overworld", "the_nether", "the_end");

    public RuntimeConfiguration {
        if (expectedProviderCount < 0 || phaseTimeoutTicks < 1 || clientWorldId.isBlank()
            || clientWindowWidth < 320 || clientWindowHeight < 240 || clientFpsLimit < 10
            || clientRenderDistance < 2 || clientSimulationDistance < 2
            || !(clientStableFrameRatio >= 1.0) || clientCaptureGateFrameBudget < 1) {
            throw new IllegalArgumentException("Invalid provider count or phase timeout");
        }
        if (!WORLD_PRESETS.contains(clientWorldPreset)) {
            throw new IllegalArgumentException("Unknown client world preset: " + clientWorldPreset);
        }
        if (!DIMENSIONS.contains(clientDimension)) {
            throw new IllegalArgumentException("Unknown client dimension: " + clientDimension);
        }
        if (serverLevelType.isBlank()) {
            throw new IllegalArgumentException("serverLevelType must not be blank");
        }
    }

    /** Source-compatible constructor for standalone callers compiled before paired mode. */
    public RuntimeConfiguration(
            Path resultDirectory, int expectedProviderCount, long seed, long phaseTimeoutTicks,
            String targetMod, String clientWorldId, boolean clientAutoWorld,
            int clientWindowWidth, int clientWindowHeight, boolean clientVsync, int clientFpsLimit,
            int clientRenderDistance, int clientSimulationDistance, boolean clientRequireWindowFocus,
            double clientStableFrameRatio, int clientCaptureGateFrameBudget,
            String clientWorldPreset, String clientDimension,
            String serverLevelType, String serverGeneratorSettings,
            boolean jfrEnabled, String scenarioFilter) {
        this(resultDirectory, expectedProviderCount, seed, phaseTimeoutTicks, targetMod,
                clientWorldId, clientAutoWorld, clientWindowWidth, clientWindowHeight,
                clientVsync, clientFpsLimit, clientRenderDistance, clientSimulationDistance,
                clientRequireWindowFocus, clientStableFrameRatio, clientCaptureGateFrameBudget,
                clientWorldPreset, clientDimension, serverLevelType, serverGeneratorSettings,
                jfrEnabled, scenarioFilter, "standalone", "");
    }

    public static RuntimeConfiguration fromSystemProperties() {
        return new RuntimeConfiguration(
                Path.of(required("modBench.resultDirectory")),
                Integer.parseInt(System.getProperty("modBench.expectedProviderCount", "1")),
                Long.parseLong(System.getProperty("modBench.seed", "0")),
                Long.parseLong(System.getProperty("modBench.phaseTimeoutTicks", "1200")),
                System.getProperty("modBench.targetMod", "unknown"),
                System.getProperty("modBench.client.worldId", "modbench-client-world"),
                Boolean.parseBoolean(System.getProperty("modBench.client.autoWorld", "true")),
                Integer.parseInt(System.getProperty("modBench.client.windowWidth", "1280")),
                Integer.parseInt(System.getProperty("modBench.client.windowHeight", "720")),
                Boolean.parseBoolean(System.getProperty("modBench.client.vsync", "false")),
                Integer.parseInt(System.getProperty("modBench.client.fpsLimit", "260")),
                Integer.parseInt(System.getProperty("modBench.client.renderDistance", "12")),
                Integer.parseInt(System.getProperty("modBench.client.simulationDistance", "12")),
                Boolean.parseBoolean(System.getProperty("modBench.client.requireWindowFocus", "true")),
                Double.parseDouble(System.getProperty("modBench.client.stableFrameRatio", "2.0")),
                Integer.parseInt(System.getProperty("modBench.client.captureGateFrameBudget", "900")),
                System.getProperty("modBench.client.worldPreset", "normal"),
                System.getProperty("modBench.client.dimension", "overworld"),
                System.getProperty("modBench.server.levelType", "minecraft:normal"),
                System.getProperty("modBench.server.generatorSettings", ""),
                Boolean.parseBoolean(System.getProperty("modBench.jfr", "false")),
                System.getProperty("modBench.scenarios", ""),
                System.getProperty("modBench.participantMode", "standalone"),
                System.getProperty("modBench.paired.sessionId", ""));
    }

    public boolean pairedServer() { return participantMode.equals("paired-server"); }
    public boolean remoteClient() { return participantMode.equals("remote-client"); }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + name);
        }
        return value;
    }
}