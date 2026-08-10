package com.zhongbai233.bench.gradle;

import java.io.File;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

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
    private final Property<String> pairedServerScenarios;
    private final Property<String> pairedClientScenarios;

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
        pairedServerScenarios = objects.property(String.class).convention("");
        pairedClientScenarios = objects.property(String.class).convention("");
    }

    /** Adds the matching ModBench API and Runtime dependencies automatically; disable to manage them manually. */
    public Property<Boolean> getAutomaticDependencies() { return automaticDependencies; }
    public Property<String> getTargetMod() { return targetMod; }
    public Property<File> getResultDirectory() { return resultDirectory; }
    public Property<Long> getSeed() { return seed; }
    public Property<Integer> getExpectedProviderCount() { return expectedProviderCount; }
    public Property<Long> getPhaseTimeoutTicks() { return phaseTimeoutTicks; }
    public Property<String> getClientWorldId() { return clientWorldId; }
    public Property<Boolean> getClientAutoWorld() { return clientAutoWorld; }
    public Property<Integer> getClientWindowWidth() { return clientWindowWidth; }
    public Property<Integer> getClientWindowHeight() { return clientWindowHeight; }
    public Property<Boolean> getClientVsync() { return clientVsync; }
    public Property<Integer> getClientFpsLimit() { return clientFpsLimit; }
    public Property<Integer> getClientRenderDistance() { return clientRenderDistance; }
    public Property<Integer> getClientSimulationDistance() { return clientSimulationDistance; }
    public Property<Boolean> getClientRequireWindowFocus() { return clientRequireWindowFocus; }
    public Property<Double> getClientStableFrameRatio() { return clientStableFrameRatio; }
    public Property<Integer> getClientCaptureGateFrameBudget() { return clientCaptureGateFrameBudget; }
    /** Overworld generation of the client benchmark world: {@code normal}, {@code flat}, or {@code void}. */
    public Property<String> getClientWorldPreset() { return clientWorldPreset; }
    /** Dimension the player is placed in before client scenarios: {@code overworld}, {@code the_nether}, {@code the_end}. */
    public Property<String> getClientDimension() { return clientDimension; }
    /** Dedicated server {@code level-type}, e.g. {@code minecraft:normal} or {@code minecraft:flat}. */
    public Property<String> getServerLevelType() { return serverLevelType; }
    /** Dedicated server {@code generator-settings} JSON for flat presets; empty for none. */
    public Property<String> getServerGeneratorSettings() { return serverGeneratorSettings; }
    /** Records one low-overhead JFR profile under {@code artifacts/jfr/} for each executed scenario. */
    public Property<Boolean> getJfrEnabled() { return jfrEnabled; }
    /** Comma-separated scenario ids (trailing {@code *} for prefixes); blank runs everything. */
    public Property<String> getScenarioFilter() { return scenarioFilter; }
    /** Address exposed to the separate client. The passthrough MVP supports loopback only. */
    public Property<String> getPairedHost() { return pairedHost; }
    /** Fixed paired port, or {@code 0} to allocate a free loopback port per run. */
    public Property<Integer> getPairedPort() { return pairedPort; }
    public Property<Integer> getPairedStartupTimeoutSeconds() { return pairedStartupTimeoutSeconds; }
    public Property<Integer> getPairedClientTimeoutSeconds() { return pairedClientTimeoutSeconds; }
    public Property<String> getPairedServerScenarios() { return pairedServerScenarios; }
    public Property<String> getPairedClientScenarios() { return pairedClientScenarios; }
}