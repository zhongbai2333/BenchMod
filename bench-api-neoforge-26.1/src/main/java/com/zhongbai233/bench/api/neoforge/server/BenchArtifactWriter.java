package com.zhongbai233.bench.api.neoforge.server;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Lets a scenario attach its own files to the report as first-class artifacts.
 *
 * <p>Registered artifacts appear in {@code summary.json} with content hash and size, are covered
 * by report verification, and are collected into the run bundle — unlike files written straight
 * into the result directory, which the report cannot vouch for.
 */
public interface BenchArtifactWriter {
    /** Writes UTF-8 content to {@code artifacts/custom/<filename>} and registers it. */
    Path write(String filename, String contentType, String content) throws IOException;

    /** Writes bytes to {@code artifacts/custom/<filename>} and registers it. */
    Path write(String filename, String contentType, byte[] content) throws IOException;

    /** Registers a file the scenario already produced inside the result directory. */
    void register(Path file, String contentType) throws IOException;
}
