package com.zhongbai233.example;

import com.zhongbai233.bench.api.BenchApiVersion;
import com.zhongbai233.bench.api.BenchCompatibility;
import com.zhongbai233.bench.api.BenchMetricDescriptor;
import com.zhongbai233.bench.api.MetricDirection;
import com.zhongbai233.bench.api.ScenarioDescriptor;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPath;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPlayback;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import com.zhongbai233.bench.api.neoforge.client.BenchClientProvider;
import com.zhongbai233.bench.api.neoforge.client.BenchEasing;
import com.zhongbai233.bench.api.neoforge.client.BenchImageDiff;
import com.zhongbai233.bench.api.neoforge.client.BenchClientRegistrar;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import com.zhongbai233.bench.api.neoforge.server.BenchServerContext;
import com.zhongbai233.bench.api.neoforge.server.BenchServerProvider;
import com.zhongbai233.bench.api.neoforge.server.BenchServerRegistrar;
import com.zhongbai233.bench.api.neoforge.server.BenchServerScenario;
import com.zhongbai233.bench.api.neoforge.server.BenchStepResult;
import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class SimpleBenchProvider implements BenchServerProvider, BenchClientProvider {
    public SimpleBenchProvider() {}

    @Override public String id() { return "simplebench"; }
    @Override public BenchCompatibility compatibility() { return BenchApiVersion.currentCompatibility(); }

    @Override
    public void registerServer(BenchServerRegistrar registrar) {
        registrar.register(
                new ScenarioDescriptor("simplebench.server-smoke", "Simple server smoke", Set.of("example"), Duration.ofSeconds(10)),
                context -> new Scenario());
    }

        @Override
        public void registerClient(BenchClientRegistrar registrar) {
        registrar.register(
            new ScenarioDescriptor("simplebench.client-render-smoke", "Simple client render smoke",
                Set.of("example", "client", "render"), Duration.ofSeconds(10)),
            context -> new ClientScenario());
        }

    private static final class Scenario implements BenchServerScenario {
        /** Custom per-tick workload metric proving the Provider metric path end to end. */
        private static final BenchMetricDescriptor WORKLOAD_ENTITIES = new BenchMetricDescriptor(
                "simplebench.workload.loaded_entities", "count", MetricDirection.NEUTRAL);
        private static final int MEASURE_TICKS = 40;

        private int stabilizeTicks;
        private int measureTicks;

        @Override
        public void setup(BenchServerContext context) {
            if (!context.server().isRunning()) {
                throw new IllegalStateException("Server is not running");
            }
        }

        @Override
        public BenchStepResult stabilize(BenchServerContext context) {
            return ++stabilizeTicks >= 2 ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
        }

        @Override
        public BenchStepResult warmup(BenchServerContext context) {
            return BenchStepResult.COMPLETE;
        }

        @Override
        public BenchStepResult measure(BenchServerContext context) {
            int loadedEntities = 0;
            for (var ignored : context.level().getAllEntities()) loadedEntities++;
            context.metrics().record(WORKLOAD_ENTITIES, loadedEntities);
            return ++measureTicks >= MEASURE_TICKS ? BenchStepResult.COMPLETE : BenchStepResult.CONTINUE;
        }

        @Override
        public void verify(BenchServerContext context) {
            if (stabilizeTicks < 2) throw new AssertionError("Stabilization workload did not run");
            if (measureTicks < MEASURE_TICKS) throw new AssertionError("Measurement window was cut short");
        }
    }

    private static final class ClientScenario implements BenchClientScenario {
        private static final String APPROACH_CAPTURE = "simple-client-render-smoke";
        private static final String ORBIT_CAPTURE = "simple-client-render-orbit";
        private static final String HUD_FREE_CAPTURE = "simple-client-render-hud-free";

        private static final BenchMetricDescriptor CAMERA_PATH_TICKS = new BenchMetricDescriptor(
                "simplebench.camera.path_ticks", "ticks", MetricDirection.NEUTRAL);
        private static final BenchMetricDescriptor SCREENSHOT_DIFF_RATIO = new BenchMetricDescriptor(
                "simplebench.screenshot.diff_ratio", "ratio", MetricDirection.NEUTRAL);

        private BenchClientPose anchor;
        private BenchCameraPlayback playback;
        private CompletableFuture<Path> hudFreeCapture;
        private boolean pathTicksRecorded;

        @Override
        public void setup(BenchClientContext context) {
            BenchClientPose current = context.automation().pose();
            anchor = new BenchClientPose(current.x(), current.y(), current.z(), 45.0F, -10.0F);
            context.automation().stopMovement();
            context.automation().setPose(anchor);
        }

        @Override
        public BenchClientStepResult stabilize(BenchClientContext context) {
            context.automation().stopMovement();
            context.automation().setPose(anchor);
            // Terrain meshes, resources and frame pacing must settle before anything is measured.
            return context.environment().readiness().ready() && context.frames().sampleCount() >= 2
                    ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
        }

        @Override
        public BenchClientStepResult warmup(BenchClientContext context) {
            context.automation().stopMovement();
            context.automation().setPose(anchor);
            return BenchClientStepResult.COMPLETE;
        }

        @Override
        public BenchClientStepResult measure(BenchClientContext context) {
            if (playback == null) {
                playback = context.automation().playPath(
                        cameraPath(), BenchCaptureOptions.defaults().withHiddenHud(false));
            }
            if (!playback.advance() || !playback.capturesComplete()) return BenchClientStepResult.CONTINUE;
            if (!pathTicksRecorded) {
                pathTicksRecorded = true;
                context.metrics().record(CAMERA_PATH_TICKS, playback.elapsedTicks());
            }
            if (hudFreeCapture == null) {
                hudFreeCapture = context.automation().captureScreenshot(
                        HUD_FREE_CAPTURE, BenchCaptureOptions.defaults());
                return BenchClientStepResult.CONTINUE;
            }
            return hudFreeCapture.isDone() ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
        }

        @Override
        public void verify(BenchClientContext context) {
            if (context.frames().sampleCount() == 0) {
                throw new AssertionError("No rendered frames were sampled");
            }
            if (!context.environment().readiness().ready()) {
                throw new AssertionError("Render pipeline was not ready: "
                        + context.environment().readiness().pendingReason());
            }
            Path approach = requireScreenshot(playback.captures().get(0));
            Path orbit = requireScreenshot(playback.captures().get(1));
            requireScreenshot(hudFreeCapture);
            try {
                BenchImageDiff diff = BenchImageDiff.compare(approach, orbit);
                // The two stops are 90 degrees of yaw apart, so far more than 1% of pixels must move.
                if (diff.differingRatio() < 0.01) {
                    throw new AssertionError("Camera stops look identical: " + diff);
                }
                context.metrics().record(SCREENSHOT_DIFF_RATIO, diff.differingRatio());
                context.artifacts().write("camera-diff.csv", "text/csv",
                        "differingRatio,meanAbsoluteError\n"
                                + diff.differingRatio() + "," + diff.meanAbsoluteError() + "\n");
            } catch (IOException exception) {
                throw new AssertionError("Could not compare client screenshots", exception);
            }
        }

        /**
         * Rises above the spawn pose while sweeping the yaw, capturing one screenshot per stop. The
         * path only moves upwards so the camera can never end up inside terrain.
         */
        private BenchCameraPath cameraPath() {
            double x = anchor.x();
            double y = anchor.y();
            double z = anchor.z();
            return BenchCameraPath.from(anchor)
                    .to(new BenchClientPose(x, y + 1.5, z, 135.0F, -5.0F), 20, BenchEasing.EASE_IN_OUT)
                    .hold(6)
                    .capture(APPROACH_CAPTURE)
                    .toAtSpeed(new BenchClientPose(x, y + 3.0, z, 225.0F, 5.0F), 4.0, BenchEasing.SMOOTH_STEP)
                    .hold(6)
                    .capture(ORBIT_CAPTURE)
                    .build();
        }

        private static Path requireScreenshot(CompletableFuture<Path> capture) {
            Path path = capture.join();
            try {
                if (!Files.isRegularFile(path) || Files.size(path) == 0) {
                    throw new AssertionError("Client screenshot is missing or empty: " + path);
                }
            } catch (IOException exception) {
                throw new AssertionError("Could not verify client screenshot: " + path, exception);
            }
            return path;
        }
    }
}