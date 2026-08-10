package com.zhongbai233.bench.runtime.report;

import com.zhongbai233.bench.api.neoforge.server.BenchArtifactWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Writes scenario-owned files under the result directory and registers them in the report. */
public final class ScenarioArtifactWriter implements BenchArtifactWriter {
    private final Path resultDirectory;
    private final BenchReportWriter report;

    public ScenarioArtifactWriter(Path resultDirectory, BenchReportWriter report) {
        this.resultDirectory = Objects.requireNonNull(resultDirectory, "resultDirectory");
        this.report = Objects.requireNonNull(report, "report");
    }

    @Override
    public Path write(String filename, String contentType, String content) throws IOException {
        return write(filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Path write(String filename, String contentType, byte[] content) throws IOException {
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(content, "content");
        Path target = resultDirectory.resolve("artifacts").resolve("custom").resolve(safeFilename(filename));
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        registerResolved(target, contentType);
        return target;
    }

    @Override
    public void register(Path file, String contentType) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(contentType, "contentType");
        Path resolved = file.isAbsolute() ? file.normalize() : resultDirectory.resolve(file).normalize();
        if (!resolved.startsWith(resultDirectory.normalize())) {
            throw new IllegalArgumentException("Artifact must live inside the result directory: " + file);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IOException("Artifact file does not exist: " + resolved);
        }
        registerResolved(resolved, contentType);
    }

    private void registerResolved(Path file, String contentType) throws IOException {
        String relative = resultDirectory.normalize().relativize(file).toString().replace('\\', '/');
        report.addArtifact("custom", relative, contentType, sha256(file), Files.size(file));
    }

    static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Artifact filename must not be blank");
        }
        if (!Path.of(filename).getFileName().toString().equals(filename) || filename.contains("..")) {
            throw new IllegalArgumentException("Artifact filename must be a plain filename");
        }
        return filename;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable", exception);
        }
    }
}
