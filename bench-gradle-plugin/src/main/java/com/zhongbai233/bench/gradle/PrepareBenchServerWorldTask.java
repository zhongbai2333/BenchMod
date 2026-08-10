package com.zhongbai233.bench.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Pins the dedicated server world to the benchmark configuration.
 *
 * <p>Merges {@code level-seed}, {@code level-type}, and optional {@code generator-settings} into
 * the run's {@code server.properties}, and deletes the world directory whenever that provisioning
 * changes — otherwise a world generated under old settings (or a random seed) would silently keep
 * being reused and the report's seed would be a lie.
 */
@DisableCachingByDefault(because = "World provisioning must always match the current configuration")
public abstract class PrepareBenchServerWorldTask extends DefaultTask {
    private static final String MARKER_FILE = ".modbench-world-provisioning";

    public PrepareBenchServerWorldTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @Internal
    public abstract Property<Long> getSeed();

    @Internal
    public abstract Property<String> getLevelType();

    @Internal
    public abstract Property<String> getGeneratorSettings();

    @TaskAction
    public void prepare() throws IOException {
        Path gameDirectory = getGameDirectory().get().getAsFile().toPath();
        Files.createDirectories(gameDirectory);
        writeServerProperties(gameDirectory.resolve("server.properties"));
        String provisioning = getSeed().get() + "|" + getLevelType().get() + "|" + getGeneratorSettings().get();
        Path marker = gameDirectory.resolve(MARKER_FILE);
        String previous = Files.isRegularFile(marker) ? Files.readString(marker, StandardCharsets.UTF_8) : null;
        if (!provisioning.equals(previous)) {
            deleteRecursively(gameDirectory.resolve("world"));
            Files.writeString(marker, provisioning, StandardCharsets.UTF_8);
            getLogger().lifecycle("Bench server world reset for provisioning: seed={}, levelType={}",
                    getSeed().get(), getLevelType().get());
        }
    }

    private void writeServerProperties(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
        }
        properties.setProperty("level-seed", String.valueOf(getSeed().get()));
        properties.setProperty("level-type", getLevelType().get());
        if (getGeneratorSettings().get().isBlank()) {
            properties.remove("generator-settings");
        } else {
            properties.setProperty("generator-settings", getGeneratorSettings().get());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "ModBench-provisioned benchmark server");
        }
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
}
