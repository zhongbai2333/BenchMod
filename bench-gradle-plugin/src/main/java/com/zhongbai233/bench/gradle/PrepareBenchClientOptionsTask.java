package com.zhongbai233.bench.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Pre-seeds first-run client settings so unattended benchmarks skip the accessibility onboarding screen. */
@DisableCachingByDefault(because = "The game may rewrite options.txt while a client run is active")
public abstract class PrepareBenchClientOptionsTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @TaskAction
    public void prepare() throws IOException {
        Path directory = getGameDirectory().get().getAsFile().toPath();
        Files.createDirectories(directory);
        Path options = directory.resolve("options.txt");
        List<String> lines = Files.isRegularFile(options)
                ? Files.readAllLines(options, StandardCharsets.UTF_8)
                : new ArrayList<>();
        boolean replaced = false;
        List<String> updated = new ArrayList<>(lines.size() + 1);
        for (String line : lines) {
            if (line.startsWith("onboardAccessibility:")) {
                if (!replaced) updated.add("onboardAccessibility:false");
                replaced = true;
            } else {
                updated.add(line);
            }
        }
        if (!replaced) updated.add("onboardAccessibility:false");
        Files.write(options, updated, StandardCharsets.UTF_8);
        getLogger().lifecycle("MODBENCH client options prepared: {}", options);
    }
}
