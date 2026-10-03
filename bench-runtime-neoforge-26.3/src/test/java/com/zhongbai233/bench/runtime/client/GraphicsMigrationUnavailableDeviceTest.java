package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zhongbai233.bench.api.neoforge.client.*;
import com.zhongbai233.bench.api.neoforge.graphics.GraphicsMigrationSuite;
import com.zhongbai233.bench.api.neoforge.server.BenchArtifactWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Missing hardware must produce a useful BLOCKED report, never fabricated green pixels. */
class GraphicsMigrationUnavailableDeviceTest {
    @Test
    void missingGpuBlocksEveryRequiredSceneAndStillWritesReport() throws Exception {
        assertNull(RenderSystem.tryGetDevice(), "This bootstrap-only test must not initialize a renderer");
        List<String> invalidations = new ArrayList<>();
        Map<String, String> artifacts = new LinkedHashMap<>();
        BenchClientEnvironment environment = new BenchClientEnvironment() {
            public boolean isValid() { return invalidations.isEmpty(); }
            public List<String> invalidations() { return List.copyOf(invalidations); }
            public void invalidate(String reason) { invalidations.add(reason); }
            public BenchClientReadiness readiness() { throw new UnsupportedOperationException(); }
            public boolean isFrameStable(int count) { return false; }
        };
        BenchArtifactWriter writer = new BenchArtifactWriter() {
            public Path write(String name, String type, String content) {
                artifacts.put(name, content); return Path.of(name);
            }
            public Path write(String name, String type, byte[] content) {
                fail("No GPU means there must be no generated pixel artifacts: " + name); return null;
            }
            public void register(Path path, String type) { fail("Unexpected artifact " + path); }
        };
        BenchClientContext context = new BenchClientContext() {
            public net.minecraft.client.Minecraft minecraft() { throw new UnsupportedOperationException(); }
            public net.minecraft.client.multiplayer.ClientLevel level() { throw new UnsupportedOperationException(); }
            public net.minecraft.client.player.LocalPlayer player() { throw new UnsupportedOperationException(); }
            public BenchClientScheduler scheduler() { throw new UnsupportedOperationException(); }
            public com.zhongbai233.bench.api.neoforge.server.BenchMetricRecorder metrics() { throw new UnsupportedOperationException(); }
            public BenchFrameMetrics frames() { throw new UnsupportedOperationException(); }
            public BenchClientAutomation automation() { throw new UnsupportedOperationException(); }
            public BenchArtifactWriter artifacts() { return writer; }
            public BenchClientEnvironment environment() { return environment; }
            public com.zhongbai233.bench.api.neoforge.server.BenchCancellationToken cancellation() { throw new UnsupportedOperationException(); }
            public Path resultDirectory() { return Path.of("unused-negative-test"); }
            public long seed() { return 602263L; }
        };
        AtomicReference<BenchClientScenarioFactory> factory = new AtomicReference<>();
        GraphicsMigrationSuite.register((descriptor, scenarioFactory) -> factory.set(scenarioFactory));
        BenchClientScenario scenario = factory.get().create(context);
        scenario.setup(context);
        for (int i = 0; i < 7; i++) scenario.measure(context);
        scenario.verify(context);
        scenario.teardown(context);
        assertEquals(List.of("graphics-migration.json"), List.copyOf(artifacts.keySet()));
        var report = JsonParser.parseString(artifacts.get("graphics-migration.json")).getAsJsonObject();
        assertEquals("BLOCKED", report.get("status").getAsString());
        assertFalse(report.get("environmentValid").getAsBoolean());
        var scenes = report.getAsJsonArray("scenes");
        assertEquals(7, scenes.size());
        for (int i = 0; i < 6; i++) assertEquals("BLOCKED", scenes.get(i).getAsJsonObject().get("status").getAsString());
        assertEquals("SKIP", scenes.get(6).getAsJsonObject().get("status").getAsString());
    }
}
