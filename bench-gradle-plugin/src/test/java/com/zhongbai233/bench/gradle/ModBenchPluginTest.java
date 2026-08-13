package com.zhongbai233.bench.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(benchImplementation.contains(group + ":bench-api-neoforge-26.1:" + version));

        var runtimeDependencies = project.getConfigurations().getByName("benchRuntimeMod").getAllDependencies();
        assertEquals(1, runtimeDependencies.size());
        ModuleDependency runtime = (ModuleDependency) runtimeDependencies.iterator().next();
        assertEquals(group, runtime.getGroup());
        assertEquals("bench-runtime-neoforge-26.1", runtime.getName());
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

    private static Project apply() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(ModBenchPlugin.class);
        return project;
    }
}
