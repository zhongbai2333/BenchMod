package com.zhongbai233.bench.runtime.client;

import com.zhongbai233.bench.api.neoforge.client.BenchCameraKeyframe;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPath;
import com.zhongbai233.bench.api.neoforge.client.BenchCameraPlayback;
import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import com.zhongbai233.bench.api.neoforge.client.BenchClientPose;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Drives a {@link BenchCameraPath} one client tick at a time.
 *
 * <p>Keyframe captures only fire on the first forward pass, so a looping path does not accumulate an
 * unbounded number of screenshots. The timeline pauses while a keyframe capture is outstanding, so a
 * gated screenshot never drifts past the keyframe that requested it, and a finished path keeps
 * holding its last keyframe until the scenario moves the camera itself.
 */
final class CameraPathPlayer implements BenchCameraPlayback {
    private final BenchCameraPath path;
    private final PoseApplier applier;
    private final BiFunction<String, BenchCaptureOptions, CompletableFuture<Path>> capturer;
    private final BenchCaptureOptions captureOptions;
    private final BooleanSupplier ownsCamera;
    private final List<CompletableFuture<Path>> captures = new ArrayList<>();
    private long elapsedTicks = -1;
    private BenchClientPose currentPose;
    private boolean stopped;

    CameraPathPlayer(BenchCameraPath path, PoseApplier applier, BenchCaptureOptions captureOptions,
                     BiFunction<String, BenchCaptureOptions, CompletableFuture<Path>> capturer) {
        this(path, applier, captureOptions, capturer, () -> true);
    }

    CameraPathPlayer(BenchCameraPath path, PoseApplier applier, BenchCaptureOptions captureOptions,
                     BiFunction<String, BenchCaptureOptions, CompletableFuture<Path>> capturer,
                     BooleanSupplier ownsCamera) {
        this.path = Objects.requireNonNull(path, "path");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.captureOptions = Objects.requireNonNull(captureOptions, "captureOptions");
        this.capturer = Objects.requireNonNull(capturer, "capturer");
        this.ownsCamera = Objects.requireNonNull(ownsCamera, "ownsCamera");
        currentPose = path.keyframes().get(0).pose();
    }

    @Override
    public boolean advance() {
        if (!ownsCamera.getAsBoolean()) {
            stopped = true;
            return true;
        }
        if (stopped) return true;
        if (!capturesComplete()) {
            // Pin the camera so a gated keyframe capture always shows that keyframe's pose.
            applier.apply(currentPose, false);
            return false;
        }
        if (elapsedTicks >= 0 && path.isFinished(elapsedTicks)) {
            // Keep holding the last keyframe so the player cannot drift after the path ended.
            applier.apply(currentPose, false);
            return true;
        }
        elapsedTicks++;
        currentPose = path.sampleAt(elapsedTicks);
        applier.apply(currentPose, path.smooth());
        captureIfKeyframeReached();
        return isFinished();
    }

    @Override
    public boolean isFinished() {
        return stopped || (elapsedTicks >= 0 && path.isFinished(elapsedTicks));
    }

    @Override
    public long elapsedTicks() {
        return Math.max(0L, elapsedTicks);
    }

    @Override
    public BenchClientPose currentPose() {
        return currentPose;
    }

    @Override
    public List<CompletableFuture<Path>> captures() {
        return List.copyOf(captures);
    }

    @Override
    public boolean capturesComplete() {
        return captures.stream().allMatch(capture -> capture.isDone());
    }

    @Override
    public void stop() {
        stopped = true;
    }

    private void captureIfKeyframeReached() {
        if (elapsedTicks > path.totalDurationTicks()) return;
        BenchCameraKeyframe keyframe = path.keyframeAt(elapsedTicks);
        if (keyframe == null || !keyframe.capturesScreenshot()) return;
        captures.add(capturer.apply(keyframe.captureName(), captureOptions));
    }

    /** Applies one sampled pose to the live player. */
    @FunctionalInterface
    interface PoseApplier {
        void apply(BenchClientPose pose, boolean smooth);
    }
}
