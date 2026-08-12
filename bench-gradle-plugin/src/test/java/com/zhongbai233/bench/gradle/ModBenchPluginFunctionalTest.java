package com.zhongbai233.bench.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModBenchPluginFunctionalTest {
    @TempDir Path projectDirectory;

    @Test
    void createsIsolatedBenchSourceSetAndReusesConfigurationCache() throws Exception {
        write("settings.gradle.kts", "rootProject.name = \"fixture\"\n");
        write("build.gradle.kts", """
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                }
                configurations.named("benchRuntimeMod")
                modBench { automaticDependencies.set(false) }
                """);
        write("src/main/java/example/MainApi.java", """
                package example;
                public final class MainApi { public static int value() { return 42; } }
                """);
        write("src/bench/java/example/BenchOnly.java", """
                package example;
                public final class BenchOnly { public static int readMain() { return MainApi.value(); } }
                """);

        BuildResult first = run("compileBenchJava", "jar", "--configuration-cache");
        assertEquals(TaskOutcome.SUCCESS, outcome(first, ":compileBenchJava"));

        Path jar = Files.list(projectDirectory.resolve("build/libs")).findFirst().orElseThrow();
        try (JarFile productionJar = new JarFile(jar.toFile())) {
            assertTrue(productionJar.getEntry("example/MainApi.class") != null);
            assertFalse(productionJar.getEntry("example/BenchOnly.class") != null);
        }

        BuildResult second = run("compileBenchJava", "jar", "--configuration-cache");
        assertTrue(second.getOutput().contains("Configuration cache entry reused."));
    }

    @Test
    void checkRunsTheAutoRegisteredProductionJarVerification() throws Exception {
        writeMinimalProject();

        BuildResult result = run("check", "--configuration-cache");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":verifyProductionJarHasNoBenchContent"));
    }

    @Test
    void sourcesJarIsVerifiedWhenPresent() throws Exception {
        write("settings.gradle.kts", "rootProject.name = \"fixture\"\n");
        write("build.gradle.kts", """
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                }
                java { withSourcesJar() }
                modBench { automaticDependencies.set(false) }
                """);
        write("src/main/java/example/MainApi.java", """
                package example;
                public final class MainApi { public static int value() { return 42; } }
                """);
        write("src/bench/java/example/BenchOnly.java", """
                package example;
                public final class BenchOnly {}
                """);

        BuildResult result = run("verifyProductionJarHasNoBenchContent");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":sourcesJar"));
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":verifyProductionJarHasNoBenchContent"));
    }

    @Test
    void productionJarVerificationFailsOnALeakedProviderDescriptor() throws Exception {
        writeMinimalProject();
        write("src/main/resources/META-INF/services/com.zhongbai233.bench.api.BenchProvider",
                "example.LeakedProvider\n");

        BuildResult result = runAndFail("verifyProductionJarHasNoBenchContent");

        assertTrue(result.getOutput().contains("bench Provider descriptor"));
    }

    @Test
    void collectBenchArtifactsToleratesMissingRunsAndWritesManifests() throws Exception {
        writeMinimalProject();

        BuildResult result = run("collectBenchArtifacts", "--configuration-cache");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":collectBenchServerArtifacts"));
        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":collectBenchClientArtifacts"));
        for (String runType : new String[] {"server", "client"}) {
            Path manifest = projectDirectory.resolve("build/modBench/bundles/default/" + runType + "/manifest.json");
            assertTrue(Files.isRegularFile(manifest), "missing manifest for " + runType);
            assertTrue(Files.readString(manifest).contains("\"files\": ["));
        }
    }

    @Test
    void verifyBenchServerReportChecksTheDefaultReportLocation() throws Exception {
        writeMinimalProject();
        writeValidReport("PASSED");

        BuildResult result = run("verifyBenchServerReport");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":verifyBenchServerReport"));
    }

    @Test
    void verifyBenchServerReportRejectsSchemaInvalidJson() throws Exception {
        writeMinimalProject();
        writeValidReport("NOT_A_STATUS");

        BuildResult result = runAndFail("verifyBenchServerReport");

        assertTrue(result.getOutput().contains("failed schema validation"));
    }

    @Test
    void verifyBenchServerReportChecksMultipleExpectedScenarios() throws Exception {
        writeMinimalProject();
        writeValidReport("PASSED");
        write("build.gradle.kts", Files.readString(projectDirectory.resolve("build.gradle.kts")) + """

                tasks.named<com.zhongbai233.bench.gradle.VerifyBenchReportTask>("verifyBenchServerReport") {
                    expectedScenarioIds.set(listOf("fixture.server-one", "fixture.server-two"))
                }
                """);

        BuildResult result = run("verifyBenchServerReport");

        assertEquals(TaskOutcome.SUCCESS, outcome(result, ":verifyBenchServerReport"));
    }

    @Test
    void verifyBenchServerIsAStandardRunAndReportLifecycleTask() throws Exception {
        writeMinimalProject();

        BuildResult result = run("tasks", "--all");

        assertTrue(result.getOutput().contains("verifyBenchServer"));
        assertTrue(result.getOutput().contains("verifyBenchClient"));
        assertTrue(result.getOutput().contains("Runs the server benchmark and verifies its report."));
    }

    @Test
    void serverWorldIsResetWhenProvisioningChanges() throws Exception {
        write("settings.gradle.kts", "rootProject.name = \"fixture\"\n");
        write("build.gradle.kts", """
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                }
                modBench {
                    automaticDependencies.set(false)
                    seed = (findProperty("benchSeed") as String? ?: "7").toLong()
                    serverLevelType = "minecraft:flat"
                }
                """);
        Path world = projectDirectory.resolve("build/modBench/runs/default/server/world");
        Path witness = world.resolve("level.dat");

        run("prepareBenchServerWorld");
        Files.createDirectories(world);
        Files.writeString(witness, "existing world");
        run("prepareBenchServerWorld");
        assertTrue(Files.exists(witness), "unchanged provisioning must keep the world");

        run("prepareBenchServerWorld", "-PbenchSeed=8");
        assertFalse(Files.exists(witness), "a seed change must reset the world");

        String properties = Files.readString(
                projectDirectory.resolve("build/modBench/runs/default/server/server.properties"));
        assertTrue(properties.contains("level-seed=8"));
        assertTrue(properties.contains("level-type=minecraft\\:flat")
                || properties.contains("level-type=minecraft:flat"));
    }

    private void writeMinimalProject() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"fixture\"\n");
        write("build.gradle.kts", """
                plugins {
                    java
                    id("com.zhongbai233.minecraft-bench")
                }
                modBench { automaticDependencies.set(false) }
                """);
        write("src/main/java/example/MainApi.java", """
                package example;
                public final class MainApi { public static int value() { return 42; } }
                """);
    }

        private void writeValidReport(String status) throws IOException {
                write("build/modBench/raw-results/default/server/summary.json", """
                                {
                                    "schema": "1.0.0",
                                    "run": {
                                        "id": "fixture-run", "suite": "default", "runType": "server",
                                        "side": "DEDICATED_SERVER", "seed": 1,
                                        "startedAt": "2026-07-29T00:00:00Z", "finishedAt": "2026-07-29T00:00:01Z"
                                    },
                                    "environment": {
                                        "os": {}, "java": {}, "versions": {}, "loadedMods": []
                                    },
                                    "artifacts": [], "providers": [],
                                    "scenarios": [
                                        {
                                            "id": "fixture.server-one", "providerId": "fixture",
                                            "status": "PASSED", "workloadCorrect": true,
                                            "failure": null, "phases": [], "metrics": []
                                        },
                                        {
                                            "id": "fixture.server-two", "providerId": "fixture",
                                            "status": "PASSED", "workloadCorrect": true,
                                            "failure": null, "phases": [], "metrics": []
                                        }
                                    ],
                                    "summary": {"status": "%s", "counts": {}},
                                    "diagnostics": []
                                }
                                """.formatted(status));
        }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult runAndFail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
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
        Path file = projectDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
