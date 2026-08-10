package com.zhongbai233.bench.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Proves the production JAR and, when present, the sources JAR contain no bench-only content:
 * nothing from the bench source set, no Provider ServiceLoader descriptor, and no ModBench API or
 * Runtime classes.
 */
@DisableCachingByDefault(because = "Verification produces no outputs worth caching")
public abstract class VerifyProductionJarTask extends DefaultTask {
    private static final String PROVIDER_DESCRIPTOR = "META-INF/services/com.zhongbai233.bench.api.BenchProvider";
    private static final String BENCH_PACKAGE_PREFIX = "com/zhongbai233/bench/";

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    public abstract RegularFileProperty getProductionJar();

    /** The published sources JAR, checked against the bench source directories when present. */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public abstract RegularFileProperty getSourcesJar();

    /** Output roots of the bench source set; any overlap with the production JAR is a leak. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBenchClasses();

    /** Source roots of the bench source set; any overlap with the sources JAR is a leak. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getBenchSources();

    @TaskAction
    public void verify() throws Exception {
        scanArchive(getProductionJar().get().getAsFile(), collectEntries(getBenchClasses()), "Production JAR");
        if (getSourcesJar().isPresent()) {
            scanArchive(getSourcesJar().get().getAsFile(), collectEntries(getBenchSources()), "Sources JAR");
        }
    }

    private static void scanArchive(File archiveFile, Set<String> benchEntries, String label) throws Exception {
        try (ZipFile archive = new ZipFile(archiveFile)) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.equals(PROVIDER_DESCRIPTOR)) {
                    throw new IllegalStateException(label + " contains the bench Provider descriptor: " + name);
                }
                if (name.startsWith(BENCH_PACKAGE_PREFIX)) {
                    throw new IllegalStateException(label + " contains ModBench API or Runtime content: " + name);
                }
                if (benchEntries.contains(name)) {
                    throw new IllegalStateException(label + " contains bench source set content: " + name);
                }
            }
        }
    }

    private static Set<String> collectEntries(ConfigurableFileCollection roots) throws IOException {
        Set<String> entries = new HashSet<>();
        for (File root : roots.getFiles()) {
            Path rootPath = root.toPath();
            if (!Files.isDirectory(rootPath)) continue;
            try (Stream<Path> files = Files.walk(rootPath)) {
                files.filter(Files::isRegularFile)
                        .map(file -> rootPath.relativize(file).toString().replace('\\', '/'))
                        .forEach(entries::add);
            }
        }
        return entries;
    }
}
