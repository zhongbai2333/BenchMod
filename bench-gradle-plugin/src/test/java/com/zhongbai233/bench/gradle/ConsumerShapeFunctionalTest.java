package com.zhongbai233.bench.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Consumer-shape matrix: project layouts a real adopter may use, beyond our own example.
 *
 * <p>The ModDev-based fixtures resolve ModDev from the shared Gradle cache; they verify
 * configuration and task wiring without launching Minecraft.
 */
class ConsumerShapeFunctionalTest {
    @TempDir Path projectDirectory;

    @Test
    void benchRunsExistWhenModBenchIsAppliedBeforeModDev() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                    id("net.neoforged.moddev")
                }
                """);

        BuildResult result = run("tasks", "--all");

        assertTrue(result.getOutput().contains("runBenchServer"));
        assertTrue(result.getOutput().contains("runBenchClient"));
        assertTrue(result.getOutput().contains("prepareBenchServerWorld"));
    }

    @Test
    void benchRunsExistWhenModBenchIsAppliedAfterModDev() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """);

        BuildResult result = run("tasks", "--all");

        assertTrue(result.getOutput().contains("runBenchServer"));
        assertTrue(result.getOutput().contains("runBenchClient"));
    }

    @Test
    void selects263FromModDevWithoutResolvingGameArtifacts() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """);
        Path script = projectDirectory.resolve("build.gradle.kts");
        Files.writeString(script, Files.readString(script)
                .replace("26.1.2.76", "26.3.0.45-beta")
                .replace("automaticDependencies.set(false)", "automaticDependencies.set(true)") + """
                val adapterNames = configurations.getByName("benchImplementation").allDependencies.map { it.name }
                require("bench-api-neoforge-26.3" in adapterNames) { adapterNames.toString() }
                require("bench-api-neoforge-26.1" !in adapterNames) { adapterNames.toString() }
                val runtimeNames = configurations.getByName("benchRuntimeMod").allDependencies.map { it.name }
                require(runtimeNames == listOf("bench-runtime-neoforge-26.3")) { runtimeNames.toString() }
                """);
        assertEquals(TaskOutcome.SUCCESS, outcome(run("help"), ":help"));
    }

    @Test
    void pairedClientsPrepareIndependentGameDirectories() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """);

        run("prepareBenchRemoteClientOptions", "-PmodBench.internal.clientCount=2",
                "-PmodBench.internal.clientIndex=0");
        run("prepareBenchRemoteClientOptions", "-PmodBench.internal.clientCount=2",
                "-PmodBench.internal.clientIndex=1");

        assertTrue(Files.isRegularFile(projectDirectory.resolve(
                "build/modBench/runs/default/remote-client-0/options.txt")));
        assertTrue(Files.isRegularFile(projectDirectory.resolve(
                "build/modBench/runs/default/remote-client-1/options.txt")));
        assertFalse(Files.exists(projectDirectory.resolve(
                "build/modBench/runs/default/remote-client/options.txt")));
    }

    @Test
    void multiModProjectRequiresAnExplicitTargetMod() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """, """
                mods {
                    create("first") { sourceSet(sourceSets.main.get()) }
                    create("second") { sourceSet(sourceSets.main.get()) }
                }
                """, "");

        BuildResult failure = runAndFail("help");
        assertTrue(failure.getOutput().contains("modBench.targetMod is required"));
    }

    @Test
    void multiModProjectWorksOnceTheTargetModIsNamed() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """, """
                mods {
                    create("first") { sourceSet(sourceSets.main.get()) }
                    create("second") { sourceSet(sourceSets.main.get()) }
                }
                """, "modBench { targetMod = \"second\" }\n");

        BuildResult result = run("tasks", "--all");
        assertTrue(result.getOutput().contains("runBenchServer"));
    }

    @Test
    void groovyDslConsumerCompilesBenchAgainstMain() throws Exception {
        write("settings.gradle", "rootProject.name = 'groovy-fixture'\n");
        write("build.gradle", """
                plugins {
                    id 'java'
                    id 'com.zhongbai233.minecraft-bench'
                }
                modBench {
                    automaticDependencies.set(false)
                    seed = 11L
                }
                """);
        write("src/main/java/example/MainApi.java",
                "package example;\npublic final class MainApi { public static int value() { return 42; } }\n");
        write("src/bench/java/example/BenchOnly.java",
                "package example;\npublic final class BenchOnly { public static int readMain() { return MainApi.value(); } }\n");

        BuildResult result = run("compileBenchJava", "check");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":compileBenchJava"));
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":verifyProductionJarHasNoBenchContent"));
    }

    @Test
    void unicodeProjectPathCompilesAndVerifies() throws Exception {
        Path unicodeRoot = projectDirectory.resolve("小模组示例");
        Files.createDirectories(unicodeRoot);
        write(unicodeRoot, "settings.gradle.kts", "rootProject.name = \"unicode-fixture\"\n");
        write(unicodeRoot, "build.gradle.kts", """
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                }
                modBench { automaticDependencies.set(false) }
                """);
        write(unicodeRoot, "src/main/java/example/MainApi.java",
                "package example;\npublic final class MainApi { public static int value() { return 1; } }\n");
        write(unicodeRoot, "src/bench/java/example/BenchOnly.java",
                "package example;\npublic final class BenchOnly { public static int v() { return MainApi.value(); } }\n");

        BuildResult result = runner(unicodeRoot, "compileBenchJava", "verifyProductionJarHasNoBenchContent").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":compileBenchJava"));
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":verifyProductionJarHasNoBenchContent"));
    }

    @Test
    void benchRuntimeModNeverLeaksIntoTheNormalRuntimeClasspath() throws Exception {
        write("settings.gradle.kts", "rootProject.name = \"isolation-fixture\"\n");
        Files.createDirectories(projectDirectory.resolve("libs"));
        Files.writeString(projectDirectory.resolve("libs/fake-runtime.jar"), "not a real jar");
        write("build.gradle.kts", """
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                }
                modBench { automaticDependencies.set(false) }
                dependencies { add("benchRuntimeMod", files("libs/fake-runtime.jar")) }
                val assertIsolation by tasks.registering {
                    val normal = configurations.getByName("runtimeClasspath")
                    val bench = project.files(sourceSets.getByName("bench").runtimeClasspath)
                    doLast {
                        val normalNames = normal.files.map { it.name }
                        require(normalNames.none { it == "fake-runtime.jar" }) {
                            "benchRuntimeMod leaked into runtimeClasspath: " + normalNames
                        }
                        val benchNames = bench.files.map { it.name }
                        require(benchNames.any { it == "fake-runtime.jar" }) {
                            "benchRuntimeMod missing from bench runtime classpath: " + benchNames
                        }
                    }
                }
                """);
        write("src/main/java/example/MainApi.java",
                "package example;\npublic final class MainApi {}\n");

        BuildResult result = run("assertIsolation");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":assertIsolation"));
    }

    @Test
    void applyingWithoutJavaIsInertInsteadOfCrashing() throws Exception {
        write("settings.gradle.kts", "rootProject.name = \"no-java-fixture\"\n");
        write("build.gradle.kts", """
                plugins {
                    id("com.zhongbai233.minecraft-bench")
                }
                """);

        BuildResult result = run("help");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":help"));
        assertFalse(result.getOutput().contains("runBenchServer"));
    }

    @Test
    void backendDefaultsToOpenGlForIntegratedAndPairedClientsOnly() throws Exception {
        writeBackendProject("");

        run("writeBackendSelection");

        assertBackendSelection("opengl");
    }

    @Test
    void backendDslSelectsVulkanAndReusesConfigurationCache() throws Exception {
        writeBackendProject("modBench { clientGraphicsBackend.set(\" VULKAN \") }\n");

        run("writeBackendSelection", "--configuration-cache");
        assertBackendSelection("vulkan");
        BuildResult second = run("writeBackendSelection", "--configuration-cache");

        assertTrue(second.getOutput().contains("Configuration cache entry reused."));
        assertBackendSelection("vulkan");
    }

    @Test
    void commandLineBackendOverridesDslAndInvalidatesConfigurationCache() throws Exception {
        writeBackendProject("modBench { clientGraphicsBackend.set(\"vulkan\") }\n");

        run("writeBackendSelection", "--configuration-cache", "-PmodBench.client.graphicsBackend= OPENGL ");
        assertBackendSelection("opengl");
        BuildResult second = run("writeBackendSelection", "--configuration-cache",
                "-PmodBench.client.graphicsBackend= OPENGL ");
        assertTrue(second.getOutput().contains("Configuration cache entry reused."));

        run("writeBackendSelection", "--configuration-cache", "-PmodBench.client.graphicsBackend=vulkan");
        assertBackendSelection("vulkan");
    }

    @Test
    void invalidBackendCannotBecomeALaunchArgument() throws Exception {
        writeBackendProject("modBench { clientGraphicsBackend.set(\"metal\") }\n");
        BuildResult invalidDsl = runAndFail("writeBackendSelection");
        assertTrue(invalidDsl.getOutput().contains("expected opengl or vulkan"));

        // A valid command-line choice can replace an invalid DSL value.
        run("writeBackendSelection", "-PmodBench.client.graphicsBackend=vulkan");
        assertBackendSelection("vulkan");

        writeBackendProject("modBench { clientGraphicsBackend.set(\"opengl\") }\n");
        BuildResult invalidOverride = runAndFail("writeBackendSelection", "-PmodBench.client.graphicsBackend=auto");
        assertTrue(invalidOverride.getOutput().contains("expected opengl or vulkan"));
    }

    @Test
    void legacy261KeepsItsLaunchArgumentsAndRejectsVulkan() throws Exception {
        writeBackendProject("");
        Path script = projectDirectory.resolve("build.gradle.kts");
        Files.writeString(script, Files.readString(script).replace("26.3.0.45-beta", "26.1.2.76"));
        run("writeBackendSelection");
        java.util.Properties selection = new java.util.Properties();
        try (var reader = Files.newBufferedReader(projectDirectory.resolve("build/backend-selection.properties"))) {
            selection.load(reader);
        }
        for (String run : java.util.List.of("benchClient", "benchRemoteClient")) {
            assertEquals("absent", selection.getProperty(run + ".backend"));
            assertFalse(selection.getProperty(run + ".args").contains("graphicsBackend"));
        }
        Files.writeString(script, Files.readString(script)
                + "modBench { clientGraphicsBackend.set(\"vulkan\") }\n");
        assertTrue(runAndFail("help").getOutput().contains("Vulkan benchmarks require the NeoForge 26.3 adapter"));
    }

    @Test
    void manualAdaptersCanUseAnUnpublishedDevelopmentLine() throws Exception {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """);
        Path script = projectDirectory.resolve("build.gradle.kts");
        Files.writeString(script, Files.readString(script).replace("26.1.2.76", "26.2.0.88"));
        assertEquals(TaskOutcome.SUCCESS, outcome(run("help"), ":help"));
    }

    private void writeBackendProject(String backendDsl) throws IOException {
        writeModDevProject("""
                plugins {
                    java
                    id("net.neoforged.moddev")
                    id("com.zhongbai233.minecraft-bench")
                }
                """, "mods { create(\"fixturemod\") { sourceSet(sourceSets.main.get()) } }\n",
                backendDsl + """
                neoForge.runs.create("ordinaryClient") { client() }
                afterEvaluate {
                    tasks.register<org.gradle.api.tasks.WriteProperties>("writeBackendSelection") {
                        destinationFile.set(layout.buildDirectory.file("backend-selection.properties"))
                        for (name in listOf("benchClient", "benchRemoteClient", "benchServer", "benchPairedServer", "ordinaryClient")) {
                            val run = neoForge.runs.getByName(name)
                            property(name + ".backend", run.systemProperties.get()["modBench.client.graphicsBackend"] ?: "absent")
                            property(name + ".args", run.programArguments.get().joinToString("|"))
                        }
                        val paired = tasks.named<com.zhongbai233.bench.gradle.PairedBenchTask>("runBenchPaired").get()
                        property("paired.backend", paired.clientGraphicsBackend.get())
                    }
                }
                """);
        Path script = projectDirectory.resolve("build.gradle.kts");
        Files.writeString(script, Files.readString(script).replace("26.1.2.76", "26.3.0.45-beta"));
    }

    private void assertBackendSelection(String expected) throws IOException {
        java.util.Properties selection = new java.util.Properties();
        try (var reader = Files.newBufferedReader(projectDirectory.resolve("build/backend-selection.properties"))) {
            selection.load(reader);
        }
        for (String run : java.util.List.of("benchClient", "benchRemoteClient")) {
            assertEquals(expected, selection.getProperty(run + ".backend"));
            java.util.List<String> arguments = java.util.List.of(selection.getProperty(run + ".args").split("\\|"));
            assertEquals(1, java.util.Collections.frequency(arguments, "--graphicsBackend"));
            assertEquals(expected, arguments.get(arguments.indexOf("--graphicsBackend") + 1));
        }
        assertEquals(expected, selection.getProperty("paired.backend"));
        for (String run : java.util.List.of("benchServer", "benchPairedServer", "ordinaryClient")) {
            assertEquals("absent", selection.getProperty(run + ".backend"));
            assertFalse(selection.getProperty(run + ".args").contains("graphicsBackend"));
        }
    }

    private void writeModDevProject(String pluginsBlock) throws IOException {
        writeModDevProject(pluginsBlock,
                "mods { create(\"fixturemod\") { sourceSet(sourceSets.main.get()) } }\n", "");
    }

    private void writeModDevProject(String pluginsBlock, String modsBlock, String extraScript) throws IOException {
        // Both plugins come from the TestKit-injected classpath, so neither declares a version.
        write("settings.gradle.kts", "rootProject.name = \"moddev-fixture\"\n");
        write("build.gradle.kts", pluginsBlock + """
                neoForge {
                    version = "26.1.2.76"
                %s
                }
                modBench { automaticDependencies.set(false) }
                """.formatted(modsBlock.indent(4)) + extraScript);
        write("src/main/java/example/MainApi.java",
                "package example;\npublic final class MainApi {}\n");
    }

    private BuildResult run(String... arguments) {
        return runner(projectDirectory, arguments).build();
    }

    private BuildResult runAndFail(String... arguments) {
        return runner(projectDirectory, arguments).buildAndFail();
    }

    private GradleRunner runner(Path directory, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    private static TaskOutcome outcome(BuildResult result, String path) {
        BuildTask task = result.task(path);
        if (task == null) throw new AssertionError("Missing task " + path);
        return task.getOutcome();
    }

    private void write(String relativePath, String content) throws IOException {
        write(projectDirectory, relativePath, content);
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
