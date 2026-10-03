package com.zhongbai233.bench.runtime.graphics;

import com.zhongbai233.bench.api.neoforge.graphics.GraphicsMigrationSuite;

import com.google.gson.GsonBuilder;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.graphics.GraphicsMigrationFixtures;
import com.zhongbai233.bench.api.graphics.GraphicsMigrationOracle;
import com.zhongbai233.bench.api.graphics.GraphicsMigrationScene;
import com.zhongbai233.bench.api.neoforge.client.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;

/** Runtime-owned implementation: all Minecraft/GPU access stays in the transformed game loader. */
public final class GraphicsMigrationRuntime implements GraphicsMigrationSuite.Factory {
    private static final String SCENARIO_ID = GraphicsMigrationSuite.SCENARIO_ID;
    public GraphicsMigrationRuntime() {}
    @Override public BenchClientScenario create(BenchClientContext context) { return new Runner(); }

    static final class Runner implements BenchClientScenario {
        private final java.util.function.Supplier<Map<String,String>> device;
        private final java.util.function.BiFunction<GraphicsMigrationScene,Long,GraphicsMigrationGpu.Probe> start;
        private final java.util.function.Supplier<String> minecraftVersion;
        Runner() { this(GraphicsMigrationGpu::device, GraphicsMigrationGpu::start, () -> SharedConstants.getCurrentVersion().id()); }
        Runner(java.util.function.Supplier<Map<String,String>> device,
               java.util.function.BiFunction<GraphicsMigrationScene,Long,GraphicsMigrationGpu.Probe> start,
               java.util.function.Supplier<String> minecraftVersion) {
            this.device=device; this.start=start; this.minecraftVersion=minecraftVersion;
        }
        private final List<Map<String, Object>> results = new ArrayList<>();
        private final Map<String, String> environment = new LinkedHashMap<>();
        private GraphicsMigrationGpu.Probe pending;
        private int index, waitTicks;
        private boolean written;
        private String blocked = "";

        @Override public void setup(BenchClientContext context) {
            environment.put("minecraft", minecraftVersion.get());
            environment.put("requestedBackend", System.getProperty("modBench.client.graphicsBackend", "opengl").trim().toLowerCase(Locale.ROOT));
            try {
                environment.putAll(device.get());
                if (!environment.get("requestedBackend").equals(environment.get("actualBackend")))
                    blocked = "Requested graphics backend differs from the actual device (including fallback)";
            } catch (RuntimeException failure) {
                blocked = "No usable GPU device: " + failure;
                for (String key : List.of("actualBackend", "deviceName", "vendor", "driver")) environment.put(key, "unknown");
            }
            if (!blocked.isEmpty()) context.environment().invalidate(blocked);
        }

        @Override public BenchClientStepResult measure(BenchClientContext context) throws Exception {
            if (index >= GraphicsMigrationScene.values().length) return BenchClientStepResult.COMPLETE;
            GraphicsMigrationScene scene = GraphicsMigrationScene.values()[index];
            if (!blocked.isEmpty()) {
                results.add(result(scene, "BLOCKED", blocked)); index++;
                return BenchClientStepResult.CONTINUE;
            }
            try {
                if (pending == null) { pending = start.apply(scene, context.seed()); waitTicks = 0; }
                if (!pending.ready()) {
                    if (++waitTicks < 400) return BenchClientStepResult.CONTINUE;
                    results.add(result(scene, "BLOCKED", "GPU completion was not observed within 400 client ticks"));
                    context.environment().invalidate("GPU readback timeout: " + scene.id());
                } else {
                    byte[] actual = pending.read();
                    byte[] expected = GraphicsMigrationFixtures.expected(scene, context.seed());
                    var difference = GraphicsMigrationOracle.compare(expected, actual, scene.tolerance());
                    Map<String, Object> item = result(scene, difference.passed() ? "PASS" : "FAIL",
                            difference.passed() ? "" : "Readback differs from CPU reference; first bad pixel=" + difference.firstBadPixel());
                    String expectedName = scene.id() + "-expected.rgba", actualName = scene.id() + "-actual.rgba";
                    context.artifacts().write(expectedName, "application/octet-stream", expected);
                    context.artifacts().write(actualName, "application/octet-stream", actual);
                    context.artifacts().write(scene.id() + "-expected.png", "image/png", png(expected, scene.width(), scene.height()));
                    context.artifacts().write(scene.id() + "-actual.png", "image/png", png(actual, scene.width(), scene.height()));
                    byte[] heatmap = actual.clone();
                    for (int p = 0; p < heatmap.length; p += 4) {
                        int max = 0;
                        for (int c = 0; c < 4; c++) max = Math.max(max, Math.abs(Byte.toUnsignedInt(expected[p+c])-Byte.toUnsignedInt(actual[p+c])));
                        heatmap[p] = (byte) max; heatmap[p+1] = 0; heatmap[p+2] = 0; heatmap[p+3] = (byte)255;
                    }
                    context.artifacts().write(scene.id() + "-diff.png", "image/png", png(heatmap, scene.width(), scene.height()));
                    item.put("expectedArtifact", expectedName); item.put("actualArtifact", actualName);
                    item.put("expectedSha256", GraphicsMigrationOracle.sha256(expected)); item.put("actualSha256", GraphicsMigrationOracle.sha256(actual));
                    // Closing before recording checks proves all suite-owned handles were returned.
                    pending.close();
                    item.put("checks", pending.checks());
                    item.put("metrics", Map.of("badPixels", difference.badPixels(), "pixelCount", difference.pixelCount(),
                            "maxChannelError", difference.maxChannelError(), "meanAbsoluteError", difference.meanAbsoluteError(), "waitTicks", waitTicks));
                    results.add(item);
                }
            } catch (UnsupportedOperationException failure) {
                results.add(result(scene, "BLOCKED", "Required public GPU capability unavailable: " + failure));
                context.environment().invalidate("Required GPU capability unavailable: " + scene.id());
            } catch (RuntimeException | AssertionError failure) {
                results.add(result(scene, "FAIL", failure.toString()));
            }
            try { closePending(); }
            catch (RuntimeException | AssertionError failure) {
                Map<String,Object> item = results.get(results.size()-1);
                item.put("status", "FAIL"); item.put("reason", "GPU cleanup failed: " + failure);
            }
            index++;
            return index >= GraphicsMigrationScene.values().length ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
        }

        @Override public void verify(BenchClientContext context) throws Exception {
            try {
                Map<String, String> end = device.get();
                for (String key : List.of("actualBackend", "deviceName", "vendor", "driver")) {
                    if (!Objects.equals(environment.get(key), end.get(key))) context.environment().invalidate("Graphics device changed during suite: " + key);
                }
            } catch (RuntimeException failure) {
                context.environment().invalidate("GPU device unavailable at verification: " + failure);
            }
            write(context);
            if (results.stream().anyMatch(item -> "FAIL".equals(item.get("status"))))
                throw new AssertionError("Graphics migration probes failed; inspect graphics-migration.json and diff images");
        }

        @Override public void teardown(BenchClientContext context) throws Exception {
            try { closePending(); }
            finally { if (!written) write(context); }
        }

        private void closePending() {
            if (pending != null) { try { pending.close(); } finally { pending = null; } }
        }

        private void write(BenchClientContext context) throws Exception {
            while (results.size() < GraphicsMigrationScene.values().length) {
                results.add(result(GraphicsMigrationScene.values()[results.size()], "BLOCKED", "Scenario ended before this probe completed"));
            }
            Map<String, Object> reload = new LinkedHashMap<>();
            reload.put("id", "engine-resource-reload"); reload.put("revision", 1); reload.put("status", "SKIP");
            reload.put("reason", "Not implemented in revision 1. Resource-recreate verifies suite-owned handles only; it does not prove engine reload or driver-global leak freedom.");
            for (String key : List.of("width", "height")) reload.put(key, 0);
            reload.put("channels", 4); reload.put("tolerancePerChannel", 0); reload.put("maxBadPixelRatio", 0);
            for (String key : List.of("expectedSha256", "actualSha256", "expectedArtifact", "actualArtifact")) reload.put(key, "");
            reload.put("checks", Map.of()); reload.put("metrics", Map.of());
            var scenes = new ArrayList<>(results); scenes.add(reload);
            boolean fail = results.stream().anyMatch(item -> "FAIL".equals(item.get("status")));
            boolean allPassed = results.stream().allMatch(item -> "PASS".equals(item.get("status")));
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schemaVersion", "graphics-migration/1"); report.put("suiteId", "graphics-migration");
            report.put("suiteRevision", 1); report.put("scenarioId", SCENARIO_ID); report.put("seed", context.seed());
            report.put("status", fail ? "FAIL" : allPassed && context.environment().isValid() ? "PASS" : "BLOCKED");
            report.put("environment", environment); report.put("environmentValid", context.environment().isValid());
            report.put("invalidations", context.environment().invalidations()); report.put("scenes", scenes);
            context.artifacts().write("graphics-migration.json", "application/json", new GsonBuilder().setPrettyPrinting().create().toJson(report));
            written = true;
        }

        private static Map<String, Object> result(GraphicsMigrationScene scene, String status, String reason) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", scene.id()); item.put("revision", scene.revision()); item.put("status", status); item.put("reason", reason);
            item.put("width", scene.width()); item.put("height", scene.height()); item.put("channels", 4);
            item.put("tolerancePerChannel", scene.tolerance()); item.put("maxBadPixelRatio", 0);
            for (String key : List.of("expectedSha256", "actualSha256", "expectedArtifact", "actualArtifact")) item.put(key, "");
            item.put("checks", Map.of()); item.put("metrics", Map.of()); return item;
        }
    }

    static byte[] png(byte[] rgba, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<height; y++) for (int x=0; x<width; x++) {
            int p=(y*width+x)*4;
            image.setRGB(x,y, (Byte.toUnsignedInt(rgba[p+3])<<24) | (Byte.toUnsignedInt(rgba[p])<<16)
                    | (Byte.toUnsignedInt(rgba[p+1])<<8) | Byte.toUnsignedInt(rgba[p+2]));
        }
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        if (!ImageIO.write(image,"png",output)) throw new IllegalStateException("PNG encoder unavailable");
        return output.toByteArray();
    }
}
