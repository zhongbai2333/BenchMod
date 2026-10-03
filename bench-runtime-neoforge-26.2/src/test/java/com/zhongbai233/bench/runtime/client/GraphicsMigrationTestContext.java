package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.neoforge.client.*;
import com.zhongbai233.bench.api.neoforge.server.*;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** Game-loader-owned fixture: never reflect the API's Minecraft signatures in the app loader. */
record GraphicsMigrationTestContext(BenchClientEnvironment environment, BenchArtifactWriter artifacts)
        implements BenchClientContext {
    public Minecraft minecraft() { throw new UnsupportedOperationException(); }
    public ClientLevel level() { throw new UnsupportedOperationException(); }
    public LocalPlayer player() { throw new UnsupportedOperationException(); }
    public BenchClientScheduler scheduler() { throw new UnsupportedOperationException(); }
    public BenchMetricRecorder metrics() { throw new UnsupportedOperationException(); }
    public BenchFrameMetrics frames() { throw new UnsupportedOperationException(); }
    public BenchClientAutomation automation() { throw new UnsupportedOperationException(); }
    public BenchCancellationToken cancellation() { throw new UnsupportedOperationException(); }
    public Path resultDirectory() { return Path.of("unused-test-result"); }
    public long seed() { return 602263L; }
}
