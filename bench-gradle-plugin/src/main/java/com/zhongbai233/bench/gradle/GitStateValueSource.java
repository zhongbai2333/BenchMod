package com.zhongbai233.bench.gradle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

/**
 * Configuration-cache-safe git probe. Returns the HEAD commit or the dirty flag, and an empty
 * string when the project is not a git checkout or git is unavailable.
 */
public abstract class GitStateValueSource implements ValueSource<String, GitStateValueSource.Parameters> {
    /** {@code query} is either {@code commit} (HEAD hash) or {@code dirty} ({@code true}/{@code false}). */
    public interface Parameters extends ValueSourceParameters {
        DirectoryProperty getWorkingDirectory();

        Property<String> getQuery();
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public String obtain() {
        boolean dirtyQuery = "dirty".equals(getParameters().getQuery().get());
        try {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ExecResult result = getExecOperations().exec(spec -> {
                spec.setWorkingDir(getParameters().getWorkingDirectory().get().getAsFile());
                spec.setCommandLine(dirtyQuery
                        ? List.of("git", "status", "--porcelain")
                        : List.of("git", "rev-parse", "HEAD"));
                spec.setStandardOutput(stdout);
                spec.setErrorOutput(new ByteArrayOutputStream());
                spec.setIgnoreExitValue(true);
            });
            if (result.getExitValue() != 0) return "";
            String output = stdout.toString(StandardCharsets.UTF_8).trim();
            return dirtyQuery ? String.valueOf(!output.isEmpty()) : output;
        } catch (Exception exception) {
            return "";
        }
    }
}
