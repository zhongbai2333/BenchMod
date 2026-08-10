package com.zhongbai233.bench.api.neoforge.server;

import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Small immutable adapter useful to unit tests and future NeoForge server integration. */
public record BenchServerContextAdapter(
    MinecraftServer server,
    ServerLevel level,
        BenchScheduler scheduler,
        BenchMetricRecorder metrics,
        BenchArtifactWriter artifacts,
        BenchCancellationToken cancellation,
        Path resultDirectory,
        long seed) implements BenchServerContext {
    public BenchServerContextAdapter {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(resultDirectory, "resultDirectory");
    }
}