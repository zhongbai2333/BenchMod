package com.zhongbai233.bench.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ModBenchPluginTest {
    @Test
    void addsModBenchDependenciesAtThePluginVersion() {
        String group = ModBenchVersion.readGroup();
        String version = ModBenchVersion.read();
        assertFalse(group.isBlank(), "the expanded group resource must be on the test classpath");
        assertFalse(version.isBlank(), "the expanded version resource must be on the test classpath");

        Project project = apply();

        Set<String> benchImplementation = project.getConfigurations().getByName("benchImplementation")
                .getAllDependencies().stream()
                .map(dependency -> dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion())
                .collect(Collectors.toSet());
        assertTrue(benchImplementation.contains(group + ":bench-api-core:" + version));
        assertTrue(benchImplementation.contains(group + ":bench-api-neoforge-26.2:" + version));

        var runtimeDependencies = project.getConfigurations().getByName("benchRuntimeMod").getAllDependencies();
        assertEquals(1, runtimeDependencies.size());
        ModuleDependency runtime = (ModuleDependency) runtimeDependencies.iterator().next();
        assertEquals(group, runtime.getGroup());
        assertEquals("bench-runtime-neoforge-26.2", runtime.getName());
        assertEquals(version, runtime.getVersion());
        assertFalse(runtime.isTransitive(), "the runtime enters the bench classpath as a mod JAR only");
    }

    @Test
    void automaticDependenciesCanBeDisabled() {
        Project project = apply();
        project.getExtensions().getByType(ModBenchExtension.class).getAutomaticDependencies().set(false);

        assertTrue(project.getConfigurations().getByName("benchImplementation").getAllDependencies().isEmpty());
        assertTrue(project.getConfigurations().getByName("benchRuntimeMod").getAllDependencies().isEmpty());
    }

    @Test
    void pairedTaskReceivesOnlyExplicitForwardedProjectProperties() {
        Project project = apply();
        ModBenchExtension extension = project.getExtensions().getByType(ModBenchExtension.class);
        extension.getPairedProjectProperties().put("fixtureFlag", "enabled");

        PairedBenchTask task = (PairedBenchTask) project.getTasks().getByName("runBenchPaired");
        assertEquals(Map.of("fixtureFlag", "enabled"), task.getParticipantProjectProperties().get());
        assertEquals(project.getLayout().getBuildDirectory().get().getAsFile(),
                task.getBuildDirectory().get().getAsFile());
    }

    @Test
    void clientsDefaultToOpenGlAndPairedCoordinatorUsesTheSameSelection() {
        Project project = apply();
        ModBenchExtension extension = project.getExtensions().getByType(ModBenchExtension.class);
        PairedBenchTask task = (PairedBenchTask) project.getTasks().getByName("runBenchPaired");

        assertEquals("opengl", extension.getClientGraphicsBackend().get());
        assertEquals("opengl", ClientGraphicsBackend.requested(project, extension).get());
        assertEquals("opengl", task.getClientGraphicsBackend().get());
        assertEquals(java.util.List.of("-PmodBench.client.graphicsBackend=opengl"),
                task.participantProjectArguments(true));
        assertTrue(task.participantProjectArguments(false).isEmpty());
    }

    @Test
    void pairedClientsForwardNormalizedDslSelectionWithoutChangingServers() {
        Project project = apply();
        ModBenchExtension extension = project.getExtensions().getByType(ModBenchExtension.class);
        extension.getClientGraphicsBackend().set(" VULKAN ");
        extension.getPairedProjectProperties().put("fixtureFlag", "enabled");
        extension.getPairedProjectProperties().put(ClientGraphicsBackend.PROPERTY, "opengl");
        PairedBenchTask task = (PairedBenchTask) project.getTasks().getByName("runBenchPaired");

        assertEquals("vulkan", task.getClientGraphicsBackend().get());
        assertEquals(java.util.List.of("-PfixtureFlag=enabled", "-PmodBench.client.graphicsBackend=vulkan"),
                task.participantProjectArguments(true));
        assertEquals(java.util.List.of("-PfixtureFlag=enabled"), task.participantProjectArguments(false));
    }

    @Test
    void rejectsUnknownAndEmptyGraphicsBackends() {
        Project project = apply();
        ModBenchExtension extension = project.getExtensions().getByType(ModBenchExtension.class);
        for (String invalid : java.util.List.of("metal", "auto", "", "  ", "opengl,vulkan")) {
            extension.getClientGraphicsBackend().set(invalid);
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> ClientGraphicsBackend.requested(project, extension).get());
            assertTrue(failure.getMessage().contains("expected opengl or vulkan"));
        }
    }

    @Test
    void normalizationDoesNotDependOnTheDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals("opengl", ClientGraphicsBackend.normalize(" OPENGL "));
            assertEquals("vulkan", ClientGraphicsBackend.normalize(" VULKAN "));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    private static Project apply() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(ModBenchPlugin.class);
        return project;
    }
}
