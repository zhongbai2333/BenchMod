package com.zhongbai233.bench.gradle;

import net.neoforged.moddevgradle.dsl.ModDevExtension;
import net.neoforged.moddevgradle.dsl.ModModel;
import net.neoforged.moddevgradle.dsl.RunModel;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/** Loaded only after ModDev is present, keeping it an optional plugin dependency. */
final class ModDevConfigurer {
    private ModDevConfigurer() {}

    static void configure(Project project, ModBenchExtension extension) {
        project.getPlugins().withId("java", ignored -> {
            project.afterEvaluate(ignoredProject -> {
                ModDevExtension modDev = project.getExtensions().getByType(ModDevExtension.class);
                SourceSet bench = project.getExtensions().getByType(SourceSetContainer.class).getByName("bench");
                modDev.addModdingDependenciesTo(bench);
                ModModel targetMod = resolveTargetMod(modDev, extension);
                targetMod.sourceSet(bench);
                modDev.getRuns().create("benchServer", run ->
                    configureRun(project, extension, bench, targetMod, run, "server"));
                modDev.getRuns().create("benchClient", run ->
                    configureRun(project, extension, bench, targetMod, run, "client"));
                modDev.getRuns().create("benchPairedServer", run ->
                    configureRun(project, extension, bench, targetMod, run, "paired-server"));
                modDev.getRuns().create("benchRemoteClient", run ->
                    configureRun(project, extension, bench, targetMod, run, "remote-client"));
            });
        });
    }

    private static ModModel resolveTargetMod(ModDevExtension modDev, ModBenchExtension extension) {
        String requested = extension.getTargetMod().get();
        if (!requested.isBlank()) {
            return modDev.getMods().getByName(requested);
        }
        if (modDev.getMods().size() != 1) {
            throw new IllegalStateException("modBench.targetMod is required when the project declares multiple mods");
        }
        return modDev.getMods().iterator().next();
    }

    private static void configureRun(Project project, ModBenchExtension extension, SourceSet bench,
                                     ModModel targetMod, RunModel run, String runType) {
        boolean server = runType.equals("server") || runType.equals("paired-server");
        boolean client = runType.equals("client") || runType.equals("remote-client");
        boolean paired = runType.equals("paired-server") || runType.equals("remote-client");
        if (server) {
            run.server();
            run.getProgramArguments().add("--nogui");
        } else {
            run.client();
        }
        run.getSourceSet().set(bench);
        run.getLoadedMods().set(java.util.Set.of(targetMod));
        run.getGameDirectory().set(project.getLayout().getBuildDirectory()
                .dir("modBench/runs/default/" + runType));
        run.getSystemProperties().put(
                "modBench.resultDirectory", project.getLayout().getBuildDirectory()
                    .dir("modBench/raw-results/default/" + runType)
                    .map(directory -> directory.getAsFile().getAbsolutePath()));
        run.getSystemProperties().put("modBench.seed", extension.getSeed().map(String::valueOf));
        run.getSystemProperties().put(
            "modBench.expectedProviderCount", extension.getExpectedProviderCount().map(String::valueOf));
        run.getSystemProperties().put(
            "modBench.phaseTimeoutTicks", extension.getPhaseTimeoutTicks().map(String::valueOf));
        run.getSystemProperties().put("modBench.targetMod", targetMod.getName());
        String modBenchVersion = ModBenchVersion.read();
        run.getSystemProperties().put("modBench.version.plugin", modBenchVersion);
        run.getSystemProperties().put("modBench.version.apiCore", modBenchVersion);
        run.getSystemProperties().put("modBench.version.apiNeoforge", modBenchVersion);
        run.getSystemProperties().put("modBench.version.runtime", modBenchVersion);
        run.getSystemProperties().put("modBench.version.schema", "1.0.0");
        run.getSystemProperties().put("modBench.git.commit", gitState(project, "commit"));
        run.getSystemProperties().put("modBench.git.dirty", gitState(project, "dirty"));
        run.getSystemProperties().put("modBench.jfr", extension.getJfrEnabled().map(String::valueOf));
        // The whitelisted command-line override: -PmodBench.scenarios=<ids> beats the DSL value.
        run.getSystemProperties().put("modBench.scenarios",
                project.getProviders().gradleProperty("modBench.scenarios")
                        .orElse(extension.getScenarioFilter()));
        if (paired) {
            run.getSystemProperties().put("modBench.participantMode", runType);
            run.getSystemProperties().put("modBench.paired.sessionId",
                    project.getProviders().gradleProperty("modBench.internal.sessionId").orElse("manual"));
        }
        if (server) {
            run.getSystemProperties().put("modBench.server.levelType", extension.getServerLevelType());
            run.getSystemProperties().put(
                    "modBench.server.generatorSettings", extension.getServerGeneratorSettings());
        }
        if (runType.equals("paired-server")) {
            run.getProgramArguments().add("--port");
            run.getProgramArguments().add(project.getProviders().gradleProperty("modBench.internal.pairedPort")
                .orElse(extension.getPairedPort().map(String::valueOf)));
        }
        if (client) {
            run.getSystemProperties().put("modBench.client.worldId", extension.getClientWorldId());
            run.getSystemProperties().put("modBench.client.autoWorld", extension.getClientAutoWorld().map(String::valueOf));
            run.getSystemProperties().put("modBench.client.windowWidth", extension.getClientWindowWidth().map(String::valueOf));
            run.getSystemProperties().put("modBench.client.windowHeight", extension.getClientWindowHeight().map(String::valueOf));
            run.getSystemProperties().put("modBench.client.vsync", extension.getClientVsync().map(String::valueOf));
            run.getSystemProperties().put("modBench.client.fpsLimit", extension.getClientFpsLimit().map(String::valueOf));
            run.getSystemProperties().put("modBench.client.renderDistance", extension.getClientRenderDistance().map(String::valueOf));
            run.getSystemProperties().put(
                    "modBench.client.simulationDistance", extension.getClientSimulationDistance().map(String::valueOf));
            run.getSystemProperties().put(
                    "modBench.client.requireWindowFocus",
                    extension.getClientRequireWindowFocus().map(String::valueOf));
            run.getSystemProperties().put(
                    "modBench.client.stableFrameRatio", extension.getClientStableFrameRatio().map(String::valueOf));
            run.getSystemProperties().put(
                    "modBench.client.captureGateFrameBudget",
                    extension.getClientCaptureGateFrameBudget().map(String::valueOf));
            run.getSystemProperties().put("modBench.client.worldPreset", extension.getClientWorldPreset());
            run.getSystemProperties().put("modBench.client.dimension", extension.getClientDimension());
        }
        if (runType.equals("remote-client")) {
            run.getSystemProperties().put("modBench.client.autoWorld", "false");
            run.getProgramArguments().add(project.getProviders().gradleProperty("modBench.internal.pairedPort")
                .orElse(extension.getPairedPort().map(String::valueOf))
                .map(port -> "--quickPlayMultiplayer=" + extension.getPairedHost().get() + ":" + port));
        }
    }

    /** Captured through a ValueSource so the report carries the commit without breaking the cache. */
    private static Provider<String> gitState(Project project, String query) {
        return project.getProviders().of(GitStateValueSource.class, source -> source.parameters(parameters -> {
            parameters.getWorkingDirectory().set(project.getLayout().getProjectDirectory());
            parameters.getQuery().set(query);
        }));
    }
}