package com.zhongbai233.bench.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Bundles everything a failed or successful run left behind — the raw report directory, game
 * logs, and crash reports — into one stable directory with a manifest.
 *
 * <p>The task tolerates every input being absent so it can always run, even when Minecraft never
 * started. It never reports up-to-date: a bundle must reflect the latest run.
 */
@DisableCachingByDefault(because = "Bundles must always reflect the most recent run")
public abstract class CollectBenchArtifactsTask extends DefaultTask {
    public CollectBenchArtifactsTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /** Raw result directory written by the Runtime (reports plus screenshots and dumps). */
    @Internal
    public abstract DirectoryProperty getResultDirectory();

    /** The run's game log directory, usually {@code <gameDirectory>/logs}. */
    @Internal
    public abstract DirectoryProperty getGameLogsDirectory();

    /** The run's crash report directory, usually {@code <gameDirectory>/crash-reports}. */
    @Internal
    public abstract DirectoryProperty getCrashReportsDirectory();

    /** Destination bundle directory; recreated from scratch on every execution. */
    @Internal
    public abstract DirectoryProperty getBundleDirectory();

    @TaskAction
    public void collect() throws IOException {
        Path bundle = getBundleDirectory().get().getAsFile().toPath();
        deleteRecursively(bundle);
        Files.createDirectories(bundle);
        List<CollectedFile> files = new ArrayList<>();
        copyTree(getResultDirectory().get().getAsFile().toPath(), bundle.resolve("report"), "report", files);
        copyTree(getGameLogsDirectory().get().getAsFile().toPath(), bundle.resolve("logs"), "logs", files);
        copyTree(getCrashReportsDirectory().get().getAsFile().toPath(),
                bundle.resolve("crash-reports"), "crash-reports", files);
        Files.writeString(bundle.resolve("manifest.json"), manifest(files), StandardCharsets.UTF_8);
        getLogger().lifecycle("Collected {} bench artifact file(s) into {}", files.size(), bundle);
    }

    private static void copyTree(Path source, Path destination, String prefix, List<CollectedFile> files)
            throws IOException {
        if (!Files.isDirectory(source)) return;
        try (Stream<Path> tree = Files.walk(source)) {
            for (Path file : tree.filter(Files::isRegularFile).toList()) {
                String relative = prefix + "/" + source.relativize(file).toString().replace('\\', '/');
                Path target = destination.resolve(source.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                files.add(new CollectedFile(relative, Files.size(target)));
            }
        }
    }

    private static String manifest(List<CollectedFile> files) {
        StringBuilder out = new StringBuilder(1024);
        out.append("{\n  \"collectedAt\": \"").append(Instant.now()).append("\",\n  \"files\": [");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) out.append(',');
            CollectedFile file = files.get(i);
            out.append("\n    {\"path\": \"").append(file.path().replace("\"", "\\\""))
                    .append("\", \"bytes\": ").append(file.bytes()).append('}');
        }
        return out.append("\n  ]\n}\n").toString();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> tree = Files.walk(root)) {
            tree.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        }
    }

    private record CollectedFile(String path, long bytes) {}
}
