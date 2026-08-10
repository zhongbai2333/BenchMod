package com.zhongbai233.bench.api.neoforge.client;

import com.zhongbai233.bench.api.client.BenchCameraFraming;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.screens.Screen;

/** Runtime-owned deterministic player, camera, and framebuffer automation. */
public interface BenchClientAutomation {
    /** Moves the local player and camera to an absolute pose without interpolation. */
    void setPose(BenchClientPose pose);

    /**
     * Moves the local player and camera to an absolute pose while letting Minecraft interpolate the
     * frames between this tick and the next, which keeps a scripted camera continuous.
     */
    void movePose(BenchClientPose pose);

    /** Rotates the local player and camera to look at an absolute world position. */
    void lookAt(double x, double y, double z);

    /** Clears player input and velocity so scripted viewpoints remain stationary. */
    void stopMovement();

    /** Returns the current local-player pose. */
    BenchClientPose pose();

    /** Frames complete world-space bounds using the active viewport and applies the resulting pose once. */
    default BenchClientPose frameTarget(BenchCameraFraming framing) {
        throw new UnsupportedOperationException("Target framing is not supported by this Runtime");
    }

    /** Keeps a pose stationary across client ticks until the returned handle is released. */
    default BenchPoseHold holdPose(BenchClientPose pose) {
        throw new UnsupportedOperationException("Pose holding is not supported by this Runtime");
    }

    /** Automatically frames complete world-space bounds and keeps that camera pose stationary. */
    default BenchPoseHold holdFramedTarget(BenchCameraFraming framing) {
        throw new UnsupportedOperationException("Target framing is not supported by this Runtime");
    }

    /** Binds a camera path to the running client. The scenario advances it once per client tick. */
    BenchCameraPlayback playPath(BenchCameraPath path);

    /** Binds a camera path whose keyframe captures use an explicit capture policy. */
    BenchCameraPlayback playPath(BenchCameraPath path, BenchCaptureOptions captureOptions);

    /** Captures a screenshot using {@link BenchCaptureOptions#defaults()}. */
    CompletableFuture<Path> captureScreenshot(String name);

    /** Captures a screenshot into the report artifacts directory under an explicit policy. */
    CompletableFuture<Path> captureScreenshot(String name, BenchCaptureOptions options);

    /** Begins a scenario-scoped read-only debugging session for the expected Screen type. */
    default BenchGuiSession beginGuiSession(Class<? extends Screen> expectedScreen) {
        throw new UnsupportedOperationException("GUI debugging is not supported by this Runtime");
    }

    /** Shows or hides the in-game HUD for every following frame. */
    void setHudHidden(boolean hidden);
}
