package com.zhongbai233.bench.api.neoforge.client;

import com.zhongbai233.bench.api.neoforge.server.BenchArtifactWriter;
import com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken;
import com.zhongbai233.bench.api.neoforge.server.BenchMetricRecorder;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public record BenchClientContextAdapter(
        Minecraft minecraft,
        ClientLevel level,
        LocalPlayer player,
        BenchClientScheduler scheduler,
        BenchMetricRecorder metrics,
        BenchFrameMetrics frames,
        BenchClientAutomation automation,
        BenchArtifactWriter artifacts,
        BenchClientEnvironment environment,
        BenchCancellationToken cancellation,
        Path resultDirectory,
        long seed) implements BenchClientContext {
    public BenchClientContextAdapter {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(automation, "automation");
        Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(resultDirectory, "resultDirectory");
    }
}