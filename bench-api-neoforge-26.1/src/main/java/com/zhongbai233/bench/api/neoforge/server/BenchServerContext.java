package com.zhongbai233.bench.api.neoforge.server;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Runtime-owned context exposed only while a scenario is executing on the server side. */
public interface BenchServerContext {
    MinecraftServer server();

    ServerLevel level();

    BenchScheduler scheduler();

    BenchMetricRecorder metrics();

    BenchArtifactWriter artifacts();

    BenchCancellationToken cancellation();

    Path resultDirectory();

    long seed();
}