package com.zhongbai233.bench.gradle;

import java.io.File;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Public-DSL-only ModDev integration. It intentionally does not create a JavaExec
 * task or inspect ModDev internal task/argument files.
 */
public final class ModBenchPlugin implements Plugin<Project> {
    private static final String NEOFORGE_LINE = "26.1";

    @Override
    public void apply(Project project) {
        ModBenchExtension extension = project.getExtensions().create("modBench", ModBenchExtension.class);
        project.getPluginManager().withPlugin("java", ignored -> configure(project, extension));
        project.getPluginManager().withPlugin("net.neoforged.moddev", ignored -> ModDevConfigurer.configure(project, extension));
    }

    private static void configure(Project project, ModBenchExtension extension) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet bench = sourceSets.maybeCreate("bench");
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        bench.setCompileClasspath(project.files(main.getOutput(), bench.getCompileClasspath()));
        bench.setRuntimeClasspath(project.files(bench.getOutput(), main.getOutput(), bench.getRuntimeClasspath()));

        Configuration implementation = project.getConfigurations().getByName(bench.getImplementationConfigurationName());
        implementation.extendsFrom(project.getConfigurations().getByName("implementation"));
        Configuration runtimeOnly = project.getConfigurations().getByName(bench.getRuntimeOnlyConfigurationName());
        runtimeOnly.extendsFrom(project.getConfigurations().getByName("runtimeOnly"));
        Configuration benchRuntimeMod = project.getConfigurations().maybeCreate("benchRuntimeMod");
        bench.setRuntimeClasspath(project.files(bench.getRuntimeClasspath(), benchRuntimeMod));

        project.getTasks().named(bench.getCompileJavaTaskName()).configure(task ->
                task.dependsOn(project.getTasks().named(main.getClassesTaskName())));
        extension.getResultDirectory().convention(project.getLayout().getBuildDirectory()
                .dir("modBench/raw-results/default/server").get().getAsFile());

        addAutomaticDependencies(project, extension, bench, benchRuntimeMod);
        registerVerificationTasks(project, extension, bench);
    }

    /**
     * Injects the ModBench API into the bench compile classpath and the Runtime Mod into
     * {@code benchRuntimeMod}, pinned to this plugin's own version. Consumers that need different
     * coordinates disable {@code modBench.automaticDependencies} and declare their own.
     */
    private static void addAutomaticDependencies(Project project, ModBenchExtension extension,
                                                 SourceSet bench, Configuration benchRuntimeMod) {
        String group = ModBenchVersion.readGroup();
        String version = ModBenchVersion.read();
        if (group.isBlank() || version.isBlank()) {
            project.getLogger().info("ModBench plugin coordinates are unknown; automatic dependencies are disabled.");
            return;
        }
        Provider<List<Dependency>> apiDependencies = extension.getAutomaticDependencies().map(enabled -> enabled
                ? List.of(
                        project.getDependencies().create(group + ":bench-api-core:" + version),
                        project.getDependencies().create(
                                group + ":bench-api-neoforge-" + NEOFORGE_LINE + ":" + version))
                : List.of());
        project.getConfigurations().getByName(bench.getImplementationConfigurationName())
                .getDependencies().addAllLater(apiDependencies);
        Provider<List<Dependency>> runtimeDependencies = extension.getAutomaticDependencies().map(enabled -> {
            if (!enabled) return List.of();
            ModuleDependency runtime = (ModuleDependency) project.getDependencies()
                    .create(group + ":bench-runtime-neoforge-" + NEOFORGE_LINE + ":" + version);
            // The APIs are already on the bench classpath; the runtime enters only as a mod JAR.
            runtime.setTransitive(false);
            return List.of(runtime);
        });
        benchRuntimeMod.getDependencies().addAllLater(runtimeDependencies);
    }

    /** Registers the plan's default-suite verification, cleanup, and collection tasks. */
    private static void registerVerificationTasks(Project project, ModBenchExtension extension, SourceSet bench) {
        ProjectLayout layout = project.getLayout();
        Provider<File> serverResults = extension.getResultDirectory();
        Provider<File> clientResults = layout.getBuildDirectory()
                .dir("modBench/raw-results/default/client").map(directory -> directory.getAsFile());

        TaskProvider<VerifyProductionJarTask> verifyJar = project.getTasks().register(
                "verifyProductionJarHasNoBenchContent", VerifyProductionJarTask.class, task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Verifies the production JAR contains no bench-only content.");
                    task.getProductionJar().set(project.getTasks().named("jar", Jar.class)
                            .flatMap(jar -> jar.getArchiveFile()));
                    task.getBenchClasses().from(bench.getOutput());
                    task.getBenchSources().from(bench.getAllSource().getSourceDirectories());
                    // Resolved at graph time so the check covers the sources JAR only when one exists.
                    task.getSourcesJar().fileProvider(project.provider(() ->
                            project.getTasks().findByName("sourcesJar") instanceof Jar sourcesJar
                                    ? sourcesJar.getArchiveFile().get().getAsFile() : null));
                    task.dependsOn(project.provider(() ->
                            project.getTasks().getNames().contains("sourcesJar")
                                    ? List.of(project.getTasks().named("sourcesJar"))
                                    : List.of()));
                });
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(check -> check.dependsOn(verifyJar));

        project.getTasks().register("collectBenchArtifacts", task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Bundles benchmark reports, logs, and crash reports for every run type.");
        });
        TaskProvider<PrepareBenchServerWorldTask> prepareServerWorld = project.getTasks().register(
                "prepareBenchServerWorld", PrepareBenchServerWorldTask.class, task -> {
                    task.setDescription("Pins the dedicated server world seed and level type.");
                    task.getGameDirectory().set(layout.getBuildDirectory().dir("modBench/runs/default/server"));
                    task.getSeed().set(extension.getSeed());
                    task.getLevelType().set(extension.getServerLevelType());
                    task.getGeneratorSettings().set(extension.getServerGeneratorSettings());
                });
                TaskProvider<PrepareBenchServerWorldTask> preparePairedServerWorld = project.getTasks().register(
                                "prepareBenchPairedServerWorld", PrepareBenchServerWorldTask.class, task -> {
                                        task.setDescription("Pins the paired dedicated server world seed and level type.");
                                        task.getGameDirectory().set(layout.getBuildDirectory().dir("modBench/runs/default/paired-server"));
                                        task.getSeed().set(extension.getSeed());
                                        task.getLevelType().set(extension.getServerLevelType());
                                        task.getGeneratorSettings().set(extension.getServerGeneratorSettings());
                                });
        TaskProvider<PrepareBenchClientOptionsTask> prepareBenchClientOptions = project.getTasks().register(
                "prepareBenchClientOptions", PrepareBenchClientOptionsTask.class, task -> {
                    task.setDescription("Prepares unattended client options for benchmark runs.");
                    task.getGameDirectory().set(layout.getBuildDirectory().dir("modBench/runs/default/client"));
                });
        TaskProvider<PrepareBenchClientOptionsTask> prepareBenchRemoteClientOptions = project.getTasks().register(
                "prepareBenchRemoteClientOptions", PrepareBenchClientOptionsTask.class, task -> {
                    task.setDescription("Prepares unattended remote-client options for paired runs.");
                    task.getGameDirectory().set(layout.getBuildDirectory().dir("modBench/runs/default/remote-client"));
                });
        project.getTasks().matching(task -> task.getName().equals("runBenchServer"))
                .configureEach(run -> run.dependsOn(prepareServerWorld));
        project.getTasks().matching(task -> task.getName().equals("runBenchPairedServer"))
                .configureEach(run -> run.dependsOn(preparePairedServerWorld));
        project.getTasks().matching(task -> task.getName().equals("runBenchClient"))
                .configureEach(run -> run.dependsOn(prepareBenchClientOptions));
        project.getTasks().matching(task -> task.getName().equals("runBenchRemoteClient"))
                .configureEach(run -> run.dependsOn(prepareBenchRemoteClientOptions));
        registerRunTasks(project, "Server", "server", serverResults);
        registerRunTasks(project, "Client", "client", clientResults);
        project.getTasks().register("runBenchPaired", PairedBenchTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Runs a dedicated server and separate client as a paired passthrough benchmark.");
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            task.getOutputDirectory().set(layout.getBuildDirectory().dir("modBench/paired/default"));
            task.getHost().set(extension.getPairedHost());
            task.getConfiguredPort().set(extension.getPairedPort());
            task.getStartupTimeoutSeconds().set(extension.getPairedStartupTimeoutSeconds());
            task.getClientTimeoutSeconds().set(extension.getPairedClientTimeoutSeconds());
            task.getScenarioFilter().set(project.getProviders().gradleProperty("modBench.scenarios")
                    .orElse(extension.getScenarioFilter()));
            task.getServerScenarioFilter().set(extension.getPairedServerScenarios());
            task.getClientScenarioFilter().set(extension.getPairedClientScenarios());
        });
    }

    private static void registerRunTasks(Project project, String capitalized,
                                         String runType, Provider<File> resultDirectory) {
        ProjectLayout layout = project.getLayout();
        String runTaskName = "runBench" + capitalized;
        TaskProvider<VerifyBenchReportTask> verifyReport = project.getTasks().register(
                "verifyBench" + capitalized + "Report", VerifyBenchReportTask.class, task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Verifies the " + runType + " benchmark report.");
                    task.getReportFile().fileProvider(resultDirectory.map(dir -> new File(dir, "summary.json")));
                    task.getExpectedRunType().convention(runType);
                    // Resolved at graph time so the ordering applies only when ModDev created the run.
                    task.mustRunAfter(project.provider(() -> benchRunIfPresent(project, runTaskName)));
                });
        project.getTasks().register("verifyBench" + capitalized, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Runs the " + runType + " benchmark and verifies its report.");
            task.dependsOn(runTaskName, verifyReport);
        });
        TaskProvider<Delete> cleanResults = project.getTasks().register(
                "cleanBench" + capitalized + "Results", Delete.class, task -> {
                    task.setDescription("Deletes stale " + runType + " benchmark results before a fresh run.");
                    task.delete(resultDirectory);
                });
        TaskProvider<CollectBenchArtifactsTask> collect = project.getTasks().register(
                "collectBench" + capitalized + "Artifacts", CollectBenchArtifactsTask.class, task -> {
                    task.setDescription("Bundles " + runType + " reports, logs, and crash reports.");
                    task.getResultDirectory().set(layout.dir(resultDirectory));
                    task.getGameLogsDirectory().set(layout.getBuildDirectory()
                            .dir("modBench/runs/default/" + runType + "/logs"));
                    task.getCrashReportsDirectory().set(layout.getBuildDirectory()
                            .dir("modBench/runs/default/" + runType + "/crash-reports"));
                    task.getBundleDirectory().set(layout.getBuildDirectory()
                            .dir("modBench/bundles/default/" + runType));
                });
        project.getTasks().named("collectBenchArtifacts").configure(task -> task.dependsOn(collect));

                project.getTasks().matching(task -> task.getName().equals(runTaskName)).configureEach(run -> {
            run.dependsOn(cleanResults);
            run.finalizedBy(collect);
        });
    }

    private static java.util.List<TaskProvider<?>> benchRunIfPresent(Project project, String runTaskName) {
        return project.getTasks().getNames().contains(runTaskName)
                ? java.util.List.of(project.getTasks().named(runTaskName))
                : java.util.List.of();
    }
}
