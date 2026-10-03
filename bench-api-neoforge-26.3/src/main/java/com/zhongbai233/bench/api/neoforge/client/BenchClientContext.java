package com.zhongbai233.bench.api.neoforge.client;

import com.zhongbai233.bench.api.neoforge.server.BenchArtifactWriter;
import com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken;
import com.zhongbai233.bench.api.neoforge.server.BenchMetricRecorder;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** Runtime-owned context exposed while an integrated-client scenario is running. */
public interface BenchClientContext {
    Minecraft minecraft();

    ClientLevel level();

    LocalPlayer player();

    BenchClientScheduler scheduler();

    BenchMetricRecorder metrics();

    BenchFrameMetrics frames();

    BenchClientAutomation automation();

    BenchArtifactWriter artifacts();

    BenchClientEnvironment environment();

    BenchCancellationToken cancellation();

    Path resultDirectory();

    long seed();
}