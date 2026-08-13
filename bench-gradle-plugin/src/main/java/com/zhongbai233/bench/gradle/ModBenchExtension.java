package com.zhongbai233.bench.gradle;

import java.io.File;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.MapProperty;

/** User-facing Gradle DSL for configuring benchmark sources, runs, and paired participants. */
public abstract class ModBenchExtension {
    private final Property<Boolean> automaticDependencies;
    private final Property<String> targetMod;
    private final Property<File> resultDirectory;
    private final Property<Long> seed;
    private final Property<Integer> expectedProviderCount;
    private final Property<Long> phaseTimeoutTicks;
    private final Property<String> clientWorldId;
    private final Property<Boolean> clientAutoWorld;
    private final Property<Integer> clientWindowWidth;
    private final Property<Integer> clientWindowHeight;
    private final Property<Boolean> clientVsync;
    private final Property<Integer> clientFpsLimit;
    private final Property<Integer> clientRenderDistance;
    private final Property<Integer> clientSimulationDistance;
    private final Property<Boolean> clientRequireWindowFocus;
    private final Property<Double> clientStableFrameRatio;
    private final Property<Integer> clientCaptureGateFrameBudget;
    private final Property<String> clientWorldPreset;
    private final Property<String> clientDimension;
    private final Property<String> serverLevelType;
    private final Property<String> serverGeneratorSettings;
    private final Property<Boolean> jfrEnabled;
    private final Property<String> scenarioFilter;
    private final Property<String> pairedHost;
    private final Property<Integer> pairedPort;
    private final Property<Integer> pairedStartupTimeoutSeconds;
    private final Property<Integer> pairedClientTimeoutSeconds;
    private final Property<Integer> pairedClientCount;
    private final Property<String> pairedServerScenarios;
    private final Property<String> pairedClientScenarios;
    private final MapProperty<String, String> pairedProjectProperties;

    /**
     * Creates the extension properties and applies reproducible defaults.
     *
     * @param objects Gradle object factory used to create lazy properties
     */
    @Inject
    public ModBenchExtension(ObjectFactory objects) {
        automaticDependencies = objects.property(Boolean.class).convention(true);
        targetMod = objects.property(String.class).convention("");
        resultDirectory = objects.property(File.class);
        seed = objects.property(Long.class).convention(0L);
        expectedProviderCount = objects.property(Integer.class).convention(1);
        phaseTimeoutTicks = objects.property(Long.class).convention(1200L);
        clientWorldId = objects.property(String.class).convention("modbench-client-world");
        clientAutoWorld = objects.property(Boolean.class).convention(true);
        clientWindowWidth = objects.property(Integer.class).convention(1280);
        clientWindowHeight = objects.property(Integer.class).convention(720);
        clientVsync = objects.property(Boolean.class).convention(false);
        clientFpsLimit = objects.property(Integer.class).convention(260);
        clientRenderDistance = objects.property(Integer.class).convention(12);
        clientSimulationDistance = objects.property(Integer.class).convention(12);
        clientRequireWindowFocus = objects.property(Boolean.class).convention(true);
        clientStableFrameRatio = objects.property(Double.class).convention(2.0);
        clientCaptureGateFrameBudget = objects.property(Integer.class).convention(900);
        clientWorldPreset = objects.property(String.class).convention("normal");
        clientDimension = objects.property(String.class).convention("overworld");
        serverLevelType = objects.property(String.class).convention("minecraft:normal");
        serverGeneratorSettings = objects.property(String.class).convention("");
        jfrEnabled = objects.property(Boolean.class).convention(false);
        scenarioFilter = objects.property(String.class).convention("");
        pairedHost = objects.property(String.class).convention("127.0.0.1");
        pairedPort = objects.property(Integer.class).convention(0);
        pairedStartupTimeoutSeconds = objects.property(Integer.class).convention(180);
        pairedClientTimeoutSeconds = objects.property(Integer.class).convention(900);
        pairedClientCount = objects.property(Integer.class).convention(1);
        pairedServerScenarios = objects.property(String.class).convention("");
        pairedClientScenarios = objects.property(String.class).convention("");
        pairedProjectProperties = objects.mapProperty(String.class, String.class).convention(java.util.Map.of());
    }

    /** @return whether matching ModBench API and Runtime dependencies are added automatically */
    public Property<Boolean> getAutomaticDependencies() { return automaticDependencies; }
    /** @return target Mod id, or blank to infer the only declared Mod */
    public Property<String> getTargetMod() { return targetMod; }
    /** @return optional result directory override */
    public Property<File> getResultDirectory() { return resultDirectory; }
    /** @return deterministic benchmark seed */
    public Property<Long> getSeed() { return seed; }
    /** @return expected number of discovered providers */
    public Property<Integer> getExpectedProviderCount() { return expectedProviderCount; }
    /** @return default lifecycle phase timeout in ticks */
    public Property<Long> getPhaseTimeoutTicks() { return phaseTimeoutTicks; }
    /** @return integrated-client benchmark world id */
    public Property<String> getClientWorldId() { return clientWorldId; }
    /** @return whether the integrated-client world is created or opened automatically */
    public Property<Boolean> getClientAutoWorld() { return clientAutoWorld; }
    /** @return requested client window width */
    public Property<Integer> getClientWindowWidth() { return clientWindowWidth; }
    /** @return requested client window height */
    public Property<Integer> getClientWindowHeight() { return clientWindowHeight; }
    /** @return whether VSync is enabled during client benchmarks */
    public Property<Boolean> getClientVsync() { return clientVsync; }
    /** @return client frame-rate limit */
    public Property<Integer> getClientFpsLimit() { return clientFpsLimit; }
    /** @return client render distance in chunks */
    public Property<Integer> getClientRenderDistance() { return clientRenderDistance; }
    /** @return client simulation distance in chunks */
    public Property<Integer> getClientSimulationDistance() { return clientSimulationDistance; }
    /** @return whether losing window focus invalidates the run */
    public Property<Boolean> getClientRequireWindowFocus() { return clientRequireWindowFocus; }
    /** @return maximum stable-frame ratio accepted by the readiness gate */
    public Property<Double> getClientStableFrameRatio() { return clientStableFrameRatio; }
    /** @return maximum frames allowed for a screenshot capture gate */
    public Property<Integer> getClientCaptureGateFrameBudget() { return clientCaptureGateFrameBudget; }
    /** @return overworld generation preset: {@code normal}, {@code flat}, or {@code void} */
    public Property<String> getClientWorldPreset() { return clientWorldPreset; }
    /** @return dimension used before client scenarios: {@code overworld}, {@code the_nether}, or {@code the_end} */
    public Property<String> getClientDimension() { return clientDimension; }
    /** @return dedicated server {@code level-type}, such as {@code minecraft:normal} */
    public Property<String> getServerLevelType() { return serverLevelType; }
    /** @return dedicated server {@code generator-settings} JSON, or blank for none */
    public Property<String> getServerGeneratorSettings() { return serverGeneratorSettings; }
    /** @return whether every executed scenario records a low-overhead JFR profile */
    public Property<Boolean> getJfrEnabled() { return jfrEnabled; }
    /** @return comma-separated scenario ids; a trailing {@code *} matches prefixes */
    public Property<String> getScenarioFilter() { return scenarioFilter; }
    /** @return address exposed to separate clients; paired passthrough currently supports loopback */
    public Property<String> getPairedHost() { return pairedHost; }
    /** @return fixed paired port, or {@code 0} to allocate a free loopback port */
    public Property<Integer> getPairedPort() { return pairedPort; }
    /** @return seconds allowed for the paired server and client JVMs to become ready */
    public Property<Integer> getPairedStartupTimeoutSeconds() { return pairedStartupTimeoutSeconds; }
    /** @return seconds allowed for participant reports to complete */
    public Property<Integer> getPairedClientTimeoutSeconds() { return pairedClientTimeoutSeconds; }
    /** @return number of separately launched physical clients, from 1 through 8 */
    public Property<Integer> getPairedClientCount() { return pairedClientCount; }
    /** @return optional scenario filter used only by the paired server */
    public Property<String> getPairedServerScenarios() { return pairedServerScenarios; }
    /** @return optional scenario filter used by every paired client */
    public Property<String> getPairedClientScenarios() { return pairedClientScenarios; }
    /** @return explicit non-secret project properties forwarded to every participant build */
    public MapProperty<String, String> getPairedProjectProperties() { return pairedProjectProperties; }
}
