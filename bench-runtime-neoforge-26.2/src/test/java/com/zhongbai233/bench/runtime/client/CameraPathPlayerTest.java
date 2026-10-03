package com.zhongbai233.bench.runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhongbai233.bench.api.neoforge.client.BenchCameraPath;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPathMode;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CameraPathPlayerTest {
    private static final BenchClientPose START = new BenchClientPose(0.0, 64.0, 0.0, 0.0F, 0.0F);
    private static final BenchClientPose END = new BenchClientPose(10.0, 64.0, 0.0, 0.0F, 0.0F);

    @Test
    void appliesOnePosePerTickAndFinishesOnTheLastKeyframe() {
        Recorder recorder = new Recorder();
        CameraPathPlayer player = recorder.play(BenchCameraPath.from(START).to(END, 4).build());

        assertFalse(player.advance());
        assertFalse(player.advance());
        assertFalse(player.advance());
        assertFalse(player.advance());
        assertTrue(player.advance());
        assertTrue(player.isFinished());

        assertEquals(5, recorder.applied.size());
        assertEquals(0.0, recorder.applied.get(0).x(), 1.0E-9);
        assertEquals(5.0, recorder.applied.get(2).x(), 1.0E-9);
        assertEquals(10.0, recorder.applied.get(4).x(), 1.0E-9);
        assertEquals(4L, player.elapsedTicks());
    }

    @Test
    void smoothPathsAskForInterpolatedMovementAndSnappedOnesDoNot() {
        Recorder smooth = new Recorder();
        smooth.play(BenchCameraPath.from(START).to(END, 2).build()).advance();
        Recorder snapped = new Recorder();
        snapped.play(BenchCameraPath.from(START).to(END, 2).snapped().build()).advance();

        assertEquals(List.of(true), smooth.smoothFlags);
        assertEquals(List.of(false), snapped.smoothFlags);
    }

    @Test
    void triggersOneCapturePerKeyframeAndPausesUntilItLands() {
        Recorder recorder = new Recorder();
        CameraPathPlayer player = recorder.play(BenchCameraPath.from(START)
                .to(END, 2).capture("stop-one")
                .hold(2).capture("stop-two")
                .build());

        player.advance();
        player.advance();
        player.advance();
        assertEquals(List.of("stop-one"), List.copyOf(recorder.requested.keySet()));

        int appliedBeforeRelease = recorder.applied.size();
        player.advance();
        assertEquals(appliedBeforeRelease + 1, recorder.applied.size(), "a blocked tick still pins the pose");
        assertEquals(2L, player.elapsedTicks(), "the timeline must not advance while a capture is pending");
        assertFalse(recorder.smoothFlags.get(appliedBeforeRelease), "the pinned pose is snapped");

        recorder.complete("stop-one");
        player.advance();
        player.advance();
        assertEquals(List.of("stop-one", "stop-two"), List.copyOf(recorder.requested.keySet()));
        assertEquals(2, player.captures().size());
    }

    @Test
    void loopingPathsCaptureOnlyOnTheFirstPass() {
        Recorder recorder = new Recorder();
        CameraPathPlayer player = recorder.play(BenchCameraPath.from(START)
                .to(END, 2).capture("loop-shot")
                .mode(BenchCameraPathMode.LOOP)
                .build());

        for (int i = 0; i < 3; i++) {
            player.advance();
            recorder.completeAll();
        }
        for (int i = 0; i < 10; i++) player.advance();

        assertFalse(player.isFinished());
        assertEquals(1, recorder.requested.size());
    }

    @Test
    void aFinishedPathKeepsHoldingItsLastKeyframe() {
        Recorder recorder = new Recorder();
        CameraPathPlayer player = recorder.play(BenchCameraPath.from(START).to(END, 2).build());

        for (int i = 0; i < 6; i++) assertTrue(player.advance() == (i >= 2));

        assertEquals(6, recorder.applied.size());
        assertEquals(2L, player.elapsedTicks());
        for (BenchClientPose pose : recorder.applied.subList(2, 6)) assertEquals(10.0, pose.x(), 1.0E-9);
        assertEquals(List.of(false, false, false), recorder.smoothFlags.subList(3, 6), "held poses are snapped");
    }

    @Test
    void stopFreezesPlayback() {
        Recorder recorder = new Recorder();
        CameraPathPlayer player = recorder.play(BenchCameraPath.from(START).to(END, 10).build());

        player.advance();
        player.stop();

        assertTrue(player.isFinished());
        assertTrue(player.advance());
        assertEquals(1, recorder.applied.size());
    }

    @Test
    void stalePlaybackStopsWithoutOverwritingTheNewCameraOwner() {
        Recorder recorder = new Recorder();
        boolean[] ownsCamera = {true};
        CameraPathPlayer player = new CameraPathPlayer(
                BenchCameraPath.from(START).to(END, 10).build(), recorder::apply,
                BenchCaptureOptions.defaults(), recorder::capture, () -> ownsCamera[0]);

        player.advance();
        ownsCamera[0] = false;

        assertTrue(player.advance());
        assertTrue(player.isFinished());
        assertEquals(1, recorder.applied.size());
    }

    private static final class Recorder {
        private final List<BenchClientPose> applied = new ArrayList<>();
        private final List<Boolean> smoothFlags = new ArrayList<>();
        private final Map<String, CompletableFuture<Path>> requested = new LinkedHashMap<>();

        CameraPathPlayer play(BenchCameraPath path) {
            return new CameraPathPlayer(path, this::apply, BenchCaptureOptions.defaults(), this::capture);
        }

        private void apply(BenchClientPose pose, boolean smooth) {
            applied.add(pose);
            smoothFlags.add(smooth);
        }

        private CompletableFuture<Path> capture(String name, BenchCaptureOptions options) {
            CompletableFuture<Path> future = new CompletableFuture<>();
            requested.put(name, future);
            return future;
        }

        void complete(String name) {
            requested.get(name).complete(Path.of(name + ".png"));
        }

        void completeAll() {
            requested.forEach((name, future) -> future.complete(Path.of(name + ".png")));
        }
    }
}
