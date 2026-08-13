package com.zhongbai233.bench.runtime;

import java.nio.file.Path;

/**
 * Immutable runtime settings shared by server, integrated-client, and paired participants.
 *
 * @param resultDirectory directory that receives reports and artifacts
 * @param expectedProviderCount expected number of discoverable benchmark providers
 * @param seed deterministic run seed
 * @param phaseTimeoutTicks default phase timeout in game ticks
 * @param targetMod Mod id under test
 * @param clientWorldId integrated-client world id
 * @param clientAutoWorld whether the integrated-client world is opened automatically
 * @param clientWindowWidth requested client window width
 * @param clientWindowHeight requested client window height
 * @param clientVsync whether VSync is enabled
 * @param clientFpsLimit requested frame-rate limit
 * @param clientRenderDistance render distance in chunks
 * @param clientSimulationDistance simulation distance in chunks
 * @param clientRequireWindowFocus whether loss of focus invalidates a run
 * @param clientStableFrameRatio maximum ratio accepted by the stable-frame gate
 * @param clientCaptureGateFrameBudget maximum frames allowed for screenshot readiness
 * @param clientWorldPreset overworld preset: {@code normal}, {@code flat}, or {@code void}
 * @param clientDimension target dimension for client scenarios
 * @param serverLevelType dedicated-server level type
 * @param serverGeneratorSettings dedicated-server generator settings JSON
 * @param jfrEnabled whether scenarios record JFR artifacts
 * @param scenarioFilter comma-separated scenario filter
 * @param participantMode standalone, paired-server, or remote-client mode
 * @param pairedSessionId coordinator session id, or blank outside paired mode
 * @param pairedClientIndex zero-based physical-client index
 * @param pairedClientCount number of physical clients participating in the paired run
 */
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
        String pairedSessionId,
        int pairedClientIndex,
        int pairedClientCount) {
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
        if (pairedClientCount < 1 || pairedClientIndex < 0 || pairedClientIndex >= pairedClientCount) {
            throw new IllegalArgumentException("Invalid paired client index/count: "
                    + pairedClientIndex + "/" + pairedClientCount);
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

    /**
     * Creates standalone settings without paired-participant metadata.
     *
     * @param resultDirectory directory that receives reports and artifacts
     * @param expectedProviderCount expected number of providers
     * @param seed deterministic run seed
     * @param phaseTimeoutTicks default phase timeout
     * @param targetMod Mod id under test
     * @param clientWorldId integrated-client world id
     * @param clientAutoWorld whether to open the world automatically
     * @param clientWindowWidth requested window width
     * @param clientWindowHeight requested window height
     * @param clientVsync whether VSync is enabled
     * @param clientFpsLimit frame-rate limit
     * @param clientRenderDistance render distance in chunks
     * @param clientSimulationDistance simulation distance in chunks
     * @param clientRequireWindowFocus whether focus loss invalidates the run
     * @param clientStableFrameRatio accepted stable-frame ratio
     * @param clientCaptureGateFrameBudget screenshot gate frame budget
     * @param clientWorldPreset overworld generation preset
     * @param clientDimension target client dimension
     * @param serverLevelType dedicated-server level type
     * @param serverGeneratorSettings dedicated-server generator settings JSON
     * @param jfrEnabled whether to record JFR artifacts
     * @param scenarioFilter comma-separated scenario filter
     */
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
                jfrEnabled, scenarioFilter, "standalone", "", 0, 1);
    }

    /** @return validated settings read from the {@code modBench.*} system properties */
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
                System.getProperty("modBench.paired.sessionId", ""),
                Integer.parseInt(System.getProperty("modBench.paired.clientIndex", "0")),
                Integer.parseInt(System.getProperty("modBench.paired.clientCount", "1")));
    }

    /** @return whether this process is the authoritative paired server */
    public boolean pairedServer() { return participantMode.equals("paired-server"); }
    /** @return whether this process is a separately launched paired client */
    public boolean remoteClient() { return participantMode.equals("remote-client"); }
    /** @return stable report/run directory name for this participant */
    public String participantRunType() {
        if (!remoteClient()) return pairedServer() ? "paired-server" : participantMode;
        return pairedClientCount == 1 ? "remote-client" : "remote-client-" + pairedClientIndex;
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + name);
        }
        return value;
    }
}
